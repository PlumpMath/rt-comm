(ns rt-comm.components.ws-handler-aleph-main
  (:require [rt-comm.incoming.connect-auth :refer [connect-process auth-process]] 

            [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]]
            [rt-comm.utils.async :as au]

            [rt-comm.init-ws-user :as init-ws-user]

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

(def time-out 3000)


#_(defn make-handler [init-ws-user-args]
  (fn ws-handler [request]  ;; client requests a ws connection here

    (let [ws-user-args {:on-open-user-socket (http/websocket-connection request)
                        :server              :aleph
                        :server-snd-fn       s/put! 
                        :server-close-fn     s/close!}]

      (spawn-fiber init-ws-user/connect-auth-init! (merge init-ws-user-args ws-user-args) time-out))))


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


