(ns rt-comm.components.ws-handler-aleph-main
  (:require [rt-comm.auth :refer [check-authentification non-websocket-request]] 

            [com.stuartsierra.component :as component]

            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :refer [rcv sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.core.match :refer [match]]
            [taoensso.timbre :refer [debug info error spy]]
            ))


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
;;
;;
;; (defn make-handler [ws-conns event-queue]
;;   "The ws-handler will connect and auth the requesting ws-client and 
;;   then call init.."
;;   (fn connect-and-auth [request]  ;; client requests a ws connection here
;;     ;; Return a deferred -> async handler
;;     ;; 1. CONNECT:
;;     (let [conn-d (http/websocket-connection request) 
;;           auth   (-> (d/chain conn-d       ;; Async 1: Wait for connection
;;                               #(s/take! %) ;; Async 2: Wait for first message
;;                               check-authentification) ;; Returns :failed, [:success user-id], ..
;;                      (d/timeout! 10000 :timed-out) ;; Connection and auth must be competed within timeout
;;                      (d/catch (fn [e] :conn-error))) ;; Catch non-WS requests. Other errors? 
;;
;;           send-msg-close #(d/future (do (s/put!   @conn-d %) 
;;                                         (s/close! @conn-d) nil))] ;; Ws-handler will return nil - see notes/question
;;       ;; 2. AUTH:
;;       (d/chain auth 
;;                #(match [%] ;; Handle outcome of auth process
;;                        [:conn-error]  non-websocket-request ;; Return Http response on error
;;                        [:timed-out]   (send-msg-close "Authentification timed out! Disconnecting.")
;;                        [:no-auth-cmd] (send-msg-close "Expected auth. command not found! Disconnecting.")
;;                        [:failed]      (send-msg-close "User-id - password login failed! Disconnecting.")
;;                        [:conn-closed] (info "Ws client closed connection before auth.")
;;
;;                        [[:success user-id]] (d/future (do (s/put! @conn-d "Login success!")
;;                                                           (info (format "Ws user-id %s loged in" user-id))
;;                                                           ;; (curried-incoming-out-fn @conn-d user-id)
;;                                                           nil))
;;                        :else (error "Ws connection: Invalid auth response!" %))))))

;; TODO: use (d/on-realized) to intit actor in main thread?!

(defrecord Ws-Handler-Aleph-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler nil #_(make-handler ws-conns event-queue)))

  (stop [component] component))



