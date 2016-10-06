(ns rt-comm.components.ws-handler-aleph-main
  (:require [rt-comm.connect-auth :refer [connect-process auth-process]] 
            [rt-comm.incoming-ws-user-pulsar :as incoming-ws-user-p]
            [rt-comm.incoming-ws-user-coreasync :as incoming-ws-user-c]
            [rt-comm.incoming-ws-user-manifold :as incoming-ws-user-m]

            [com.stuartsierra.component :as component]

            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [taoensso.timbre :refer [debug info error spy]]))



#_(defn init-ws-user! [{:keys [user-socket ws-conns event-queue] :as args}]

  (let [incoming-stream (incoming-ws-user-p/incoming-stream 
                          (s/->source user-socket)
                          (select-keys args [:user-id :allowed-actns]))

        incoming-actor (spawn incoming-ws-user-p/incoming-ws-user-actor 
                              incoming-stream
                              event-queue 
                              (select-keys args [:batch-sample-intv]))

        incoming-actor (spawn incoming-ws-user-p/incoming-ws-user-actor 
                              incoming-stream
                              (partial ! event-queue)  ;; snd-event-queue function
                              (select-keys args [:batch-sample-intv]))


        outgoing-socket-sink   (s/->sink   user-socket) 

        outgoing-actor nil]

    (swap! ws-conns conj {:user-id        user-id
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


(defn make-handler [init-ws-user-args]
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


