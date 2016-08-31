(ns rt-comm.components.ws-handler-aleph-main
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]

            [clojure.core.match :refer [match]]
            [co.paralleluniverse.pulsar.core :refer [rcv sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.async :as pa]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]
            [taoensso.timbre :refer [debug info error spy]]))


;; (def non-websocket-request
;;   {:status 400
;;    :headers {"content-type" "application/text"}
;;    :body "Expected a websocket request."})
;;
;;
;; (def users [{:user-id "pete" :pw "abc"} 
;;             {:user-id "paul" :pw "cde"} 
;;             {:user-id "mary" :pw "fgh"}])
;;
;; (defn registered-user? [login]
;;   (-> (partial = login) (filter users) not-empty))
;;
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
;;
;; (defn init-ws-user []
;;   ,,,)
;;
;;
;; (def auth-init-actor
;;   "Accepts an :auth :cmd message within timeout. Spins off incoming- and
;;   outgoing actors or disconnects client accordingly."
;;   (sfn auth-init-actor [ev-queue user-socket]
;;        (receive
;;          [{:cmd [:auth login]}] (if (registered-user? login) 
;;                                   (info "user: " (:user-id login)))
;;          :else (println "got it!")
;;          :after 6000 "timed out!!")))
;;
;;  
;; (defn auth-ws-user []
;;   ,,,)
;;
;; {:cmd [:auth {:user-id "pete" :pw "abc"}]}
;;
;; (defn check-authentification [auth-message]
;;   (match [auth-message]
;;          [{:cmd [:auth login]}] (if (registered-user? login) 
;;          [[1 & r]] [:a1 r]
;;          :else 432))
;;
;; {:user-id "ab23" :room "green" :aa 12 :bb [1 2 3]}
;; {:cmd [:auth {:user-id "pete" :pw "abc"}]}
;;
;; (-> (http/websocket-connection request)
;;     (d/chain #(s/take! %)
;;              check-authentification
;;              (d/timeout! 4000 false)
;;              )
;;     (d/catch (fn [] non-websocket-request)))
;;
;; (defn make-handler [ws-conns event-queue]
;;   "The returned handler will launch auth-init-actor on ws-connection."
;;   (fn [request]  ;; client requests a ws connection here
;;     ;; Return a deferred -> async handler
;;     ;; 1. CONNECT:
;;     (d/let-flow [conn (-> (http/websocket-connection request) 
;;                           (d/catch (fn [_] nil)))]
;;       (if-not conn
;;         non-websocket-request ;; Return error to client 
;;
;;         ;; 2. AUTHORIZE USER: First message must be an :auth :cmd message
;;         (d/let-flow [auth-message (-> (s/take! conn) 
;;                                       (d/timeout! 4000 :timeout!))
;;                      authorized?  (check-authentification auth-message)]
;;
;;           (if authorized?
;;             )
;;
;;           (s/consume #(! actor-inc %) conn))
;;
;;         ))
;;
;;
;;     ;; Add
;;     ;; (connect! ws-conns user-socket)
;;
;;     ;; Remove
;;     ;; (s/on-closed user-socket #(disconnect! ws-conns user-socket))
;;
;;     ))

(defrecord Ws-Handler-Aleph-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler nil #_(make-handler ws-conns event-queue)))

  (stop [component] component))



