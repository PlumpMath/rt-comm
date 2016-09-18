(ns rt-comm.components.ws-handler-aleph-main
  (:require [rt-comm.connect-auth :refer [connect-process auth-process]] 

            [com.stuartsierra.component :as component]

            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.core.match :refer [match]]
            [taoensso.timbre :refer [debug info error spy]]
            ))

;;
;; (def ws-client-incoming-actor 
;;   "ws-client-incoming-actor"
;;   (sfn ws-client-incoming-actor [ev-queue]
;;        (loop [aa 123]
;;
;;          (receive
;;            [:append! new-events] (do
;;                                    (println "eins")
;;                                    (recur 123))
;;            ))))
;;
;; (defn init-ws-user! [user-id user-socket ws-conns ev-queue]
;;   (let [incoming-socket-source (s/->source user-socket) 
;;         outgoing-socket-sink   (s/->sink user-socket) 
;;         incoming-actor (spawn ,,,)
;;         outgoing-actor nil]
;;
;;     (swap! ws-conns conj {:user-id        user-id
;;                           :socket         user-socket
;;                           :incoming-actor incoming-actor
;;                           :outgoing-actor nil})))


(defn make-handler [ws-conns event-queue]
  (fn ws-handler [request]  ;; client requests a ws connection here

    (let [auth-ws-user-args {:on-open-user-socket (http/websocket-connection request)
                             :server              :aleph}  
          ;:user-id            nil ;; Will be provided in auth-process - auth-result
          ;:user-socket        nil ;; Will be provide by @on-open-user-socket

          init-ws-user-args {:ws-conns      ws-conns
                             :event-queue   event-queue}]

      (d/future (some-> auth-ws-user-args 
                        (connect-process 200) ;; wait for connection
                        (auth-process s/put! s/close! 200) ;; returns augmented init-ws-user-args or nil
                        (merge init-ws-user-args)
                        #_init-ws-user!)))))

;; TEST CODE: manual
;; (do
;; (def on-open-user-socket (d/deferred))
;; (def auth-ws-user-args {:on-open-user-socket on-open-user-socket
;;                         :server :aleph})  
;; (def calls (atom []))
;; (def send! (fn [ch msg] (swap! calls conj msg)))
;; (def close (fn [ch] (swap! calls conj "closed!")))
;; (def user-socket (s/stream))
;;
;; (def fib-rt (d/future (some-> auth-ws-user-args 
;;                               (connect-process 4000) 
;;                               (auth-process send! close 4000))))
;; )
;;
;; (deliver on-open-user-socket user-socket)
;; (future (s/put! user-socket {:cmd [:auth {:user-id "pete" :pw "abc"}]}))
;; (deref fib-rt)


;; TODO: use (d/on-realized) to intit actor in main thread?!

(defrecord Ws-Handler-Aleph-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler nil #_(make-handler ws-conns event-queue)))

  (stop [component] component))


