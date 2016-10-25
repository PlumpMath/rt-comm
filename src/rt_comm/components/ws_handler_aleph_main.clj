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



(defn make-handler [init-args]
  (fn ws-handler [request]  ;; client requests a ws connection here

    (let [ws-user-args {:on-open-user-socket (http/websocket-connection request)
                        :server              :aleph
                        :server-snd-fn       s/put! 
                        :server-close-fn     s/close!}]

      (spawn-fiber init-ws-user/connect-auth-init! (merge init-args ws-user-args))
      nil)))


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
      (assoc component :ws-handler (make-handler init-ws-user-args))))

  (stop [component] component))


