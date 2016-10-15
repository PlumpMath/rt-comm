(ns rt-comm.components.ws-handler-aleph-main
  (:require [rt-comm.incoming.connect-auth :refer [connect-process auth-process]] 
            ;; [rt-comm.incoming.ws-user-pulsar    :refer [incoming-ws-user-actor]]
            ;; [rt-comm.incoming.ws-user-coreasync :refer [incoming-ws-user-actor]]
            [rt-comm.incoming.ws-user-manifold  :refer [incoming-ws-user-actor]]

            [rt-comm.incoming.stateless-transform :refer [incoming-tx]]

            [rt-comm.utils.async :as au]

            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!! alt! pipe
                                              close! put! take! thread timeout
                                              offer! poll! promise-chan
                                              sliding-buffer]]

            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]
            [co.paralleluniverse.pulsar.async :as pa]

            [taoensso.timbre :refer [debug info error spy]]))




;; TODO: 
;; how to close conn?

;; MANIFOLD
;; (def s1 (s/stream))
;; (def in-tx (incoming-tx {:user-id "paul"
;;                          :allowed-actns [:aa :bb]}))
;; (def in-st (s/transform in-tx s1))
;;
;; (s/consume #(info (vec %)) in-st)
;;
;; (s/put! s1 [{:actn :aa :idx 12} 
;;             {:actn :aa :idx 13} 
;;             {:idx 14}] )
;; (s/put! s1 [{:actn :ada :idx 12} 
;;             {:idx 14}])
;;



(defn init-ws-user! [{:keys [user-socket ws-conns event-queue] :as args}]

  (let [user-socket-in  (s/->source user-socket) 

        in-tx           (-> (select-keys args [:user-id :allowed-actns])
                            incoming-tx) 

        ;; Manifold
        ;; incom-tx-stream (s/transform in-tx user-socket-in)

        ;; core.async
        incom-tx-stream (au/transf-st-ch user-socket-in in-tx)

        ;; Pulsar
        ;; incom-tx-stream (au/transf-st-pch user-socket-in in-tx)


        ;; Pulsar
        ;; incoming-actor (spawn incoming-ws-user-actor 
        ;;                       incom-tx-stream
        ;;                       event-queue
        ;;                       (select-keys args [:batch-sample-intv]))

        ;; Manifold or core.async
        incoming-actor (incoming-ws-user-actor
                         incom-tx-stream
                         #(! event-queue %)  ;; snd-event-queue function
                         (select-keys args [:batch-sample-intv]))


        outgoing-actor nil]

    (swap! ws-conns conj {:user-id        (:user-id args)
                          :socket         user-socket ;; debug only?!
                          :incoming-actor incoming-actor
                          :outgoing-actor outgoing-actor})))


;; TEST CODE:
;; (do 
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; ;; (def ev-queue [])
;; ;; (def ws-conns (-> system :ws-conns-main))
;; (def ws-conns (atom []))
;; (def user-socket (s/stream))
;; (fiber (init-ws-user! {:user-socket user-socket :user-id "pete"
;;                        :ws-conns ws-conns :event-queue ev-queue}))
;; (def in-ac (:incoming-actor (first @ws-conns)))
;; true
;; )
;;
;; (! in-ac [:append! [{:eins 11} {:zwei 22}]])


#_(defn make-handler [init-ws-user-args]
  (fn ws-handler [request]  ;; client requests a ws connection here

    (let [auth-ws-user-args {:on-open-user-socket (http/websocket-connection request)
                             :server              :aleph}]
          ;:user-id            nil ;; Will be provided in auth-process - auth-result
          ;:user-socket        nil ;; Will be provide by @on-open-user-socket

      ;; run connect- and auth processes in fiber, then init incoming-user-process
      (fiber (some-> auth-ws-user-args 
                     (connect-process 200) ;; wait for connection, assoc user-socket
                     (auth-process s/put! s/close! 200) ;; returns auth-ws-user-args with assoced user-id or nil

                     (select-keys [:user-id :user-socket])
                     (merge init-ws-user-args)
                     #_init-ws-user!)))))


;; TEST CODE: manual
;; (do
;;
;; (def on-open-user-socket (d/deferred))
;; (def auth-ws-user-args {:on-open-user-socket on-open-user-socket
;;                         :server :aleph})  
;; (def calls (atom []))
;; (def send! (fn [ch msg] (swap! calls conj msg)))
;; (def close (fn [ch] (swap! calls conj "closed!")))
;; (def user-socket (s/stream))
;;
;; (def fib-rt (fiber (some-> auth-ws-user-args 
;;                               (connect-process 4000) 
;;                               (auth-process send! close 4000))))
;; )
;;
;; (deliver on-open-user-socket user-socket)
;; (future (s/put! user-socket {:cmd [:auth {:user-id "pete" :pw "abc"}]}))
;; (deref fib-rt)
;; ;; =>
;; {:on-open-user-socket << << stream:  >> >>, 
;;  :server :aleph, 
;;  :user-socket << stream:  >>, 
;;  :auth-result [:success "Login success!" "pete"], 
;;  :auth-success true, 
;;  :user-msg "Login success!", 
;;  :user-id "pete"}

(defrecord Ws-Handler-Aleph-main [conf ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (let [init-ws-user-args (merge conf {:ws-conns     ws-conns
                                         :event-queue  event-queue})] 
      (assoc component :ws-handler nil #_(make-handler init-ws-user-args))))

  (stop [component] component))


