(ns rt-comm.components.ws-handler-aleph-main
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]

            [clojure.core.match :refer [match]]
            [taoensso.timbre :refer [debug info error spy]]))


(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})


(def users [{:user-id "pete" :pw "abc"} 
            {:user-id "paul" :pw "cde"} 
            {:user-id "mary" :pw "fgh"}])

(defn registered-user? [login]
  (-> (partial = login) (filter users) not-empty))

(defn check-authentification [auth-message]
  (info "auth: msg" auth-message)
  (match [auth-message]
         [{:cmd [:auth login]}] (if (registered-user? login) 
                                  [:success (:user-id login)] :failed)
         [:false] :conn-closed
         ["test"] [:success "test-id"] 
         :else    :no-auth-cmd))


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

(defn make-handler [ws-conns event-queue]
  "The ws-handler will connect and auth the requesting ws-client and 
  then call init.."
  (fn connect-and-auth [request]  ;; client requests a ws connection here
    ;; Return a deferred -> async handler
    ;; 1. CONNECT:
    (let [conn-d (http/websocket-connection request) 
          auth   (-> (d/chain conn-d       ;; Async 1: Wait for connection
                              #(s/take! %) ;; Async 2: Wait for first message
                              check-authentification) ;; Returns :failed, [:success user-id], ..
                     (d/timeout! 10000 :timed-out) ;; Connection and auth must be competed within timeout
                     (d/catch (fn [e] :conn-error))) ;; Catch non-WS requests. Other errors? 

          send-msg-close #(d/future (do (s/put!   @conn-d %) 
                                        (s/close! @conn-d) nil))] ;; Ws-handler will return nil - see notes/question
      ;; 2. AUTH:
      (d/chain auth 
               #(do (info "auth:" %) %) 
               #(match [%] ;; Handle outcome of auth process
                       [:conn-error]  non-websocket-request ;; Return Http response on error
                       [:timed-out]   (send-msg-close "Authentification timed out! Disconnecting.")
                       [:no-auth-cmd] (send-msg-close "Expected auth. command not found! Disconnecting.")
                       [:failed]      (send-msg-close "User-id - password login failed! Disconnecting.")
                       [:conn-closed] (info "Ws client closed connection before auth.")

                       [[:success user-id]] (d/future (do (s/put! @conn-d "Login success!")
                                                          (info (format "Ws user-id %s loged in." user-id))
                                                          ;; (curried-incoming-out-fn @conn-d user-id)
                                                          "Ws auth success return val"))
                       :else (error "Ws connection: Invalid auth response!" %))))))



(defrecord Ws-Handler-Aleph-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler (make-handler ws-conns event-queue)))

  (stop [component] component))



