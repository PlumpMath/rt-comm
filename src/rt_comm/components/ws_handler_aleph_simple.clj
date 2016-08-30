(ns rt-comm.components.ws-handler-aleph-simple
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [taoensso.timbre :refer [debug info error spy]]))


(defn connect! [connected-clients req-client-socket]
  (info "Add socket to connected-clients" #_connected-clients #_req-client-socket)
  (let [send-to-this-client-cb (partial s/put! req-client-socket)] 
    (swap! connected-clients conj {:socket req-client-socket :cb send-to-this-client-cb})))

;; TODO: how to remove the callback from the set??
(defn disconnect! [connected-clients req-client-socket]
  (info "Remove socket from connected clients")
  (swap! connected-clients #(remove (comp #{req-client-socket} :socket) %)))

(defn notify-clients! [connected-clients msg]
  (info "Broadcast message to all connected clients: " msg)
  (doseq [client @connected-clients]
    ((:cb client) (str "From server: " msg))))


(defn make-handler [connected-clients]
  "The returned handler will add and remove the user-socket-connection-stream object
  to the enclosed connected-clients atom."
  (fn [request]  ;; client requests a ws connection here
    ;; create connection to client - blocking!
    (let [user-socket @(http/websocket-connection request)]

      ;; Add
      (connect! connected-clients user-socket)

      ;; Remove
      (s/on-closed user-socket #(disconnect! connected-clients user-socket))

      ;; Receive messages from this user-socket-source
      (s/consume (partial notify-clients! connected-clients) user-socket)
      )))


(defrecord Ws-Handler-Aleph-simple [ws-clients ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler (make-handler ws-clients)))
  ;; the handler holds a reference to the state (an atom) in a closure
  ;; ws-handler therefore contains a stateful reference
  ;; ws-handler is passed into other components

  (stop [component] component))


;; TEST:
;; ws://localhost:5050/ws 
;; (require '[dev :refer [system]])
;; (def cls (-> system :ws-handler-aleph :clients))
;;
;; (doseq [client-socket @cls]
;;     (s/put! client-socket "A message!!!"))
;;
;; (-> @(-> system :ws-handler-aleph :clients)
;;     vec
;;     (get 1)
;;     (s/put! "Tee")
;;     )




