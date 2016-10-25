(ns rt-comm.components.ws-handler-aleph-simple
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [taoensso.timbre :refer [debug info error spy]]))


(defn connect! [ws-conns req-client-socket]
  (info "Add socket to ws-conns" #_req-client-socket)
  (let [send-to-this-client-cb (partial s/put! req-client-socket)] 
    (swap! ws-conns conj {:socket req-client-socket :cb send-to-this-client-cb})))

;; TODO: how to remove the callback from the set??
(defn disconnect! [ws-conns req-client-socket]
  (info "Remove socket from connected clients")
  (swap! ws-conns #(remove (comp #{req-client-socket} :socket) %)))

(defn notify-clients! [ws-conns msg]
  (info "Broadcast message to all connected clients: " msg)
  (doseq [client @ws-conns]
    ((:cb client) (str "From server: " msg))))


(defn make-handler [ws-conns]
  "The returned handler will add and remove the user-socket-connection-stream object
  to the enclosed ws-conns atom."
  (fn [request]  ;; client requests a ws connection here
    ;; create connection to client - blocking!
    (let [user-socket @(http/websocket-connection request)]

      ;; Add
      (connect! ws-conns user-socket)

      ;; Remove
      (s/on-closed user-socket #(disconnect! ws-conns user-socket))

      ;; Receive messages from this user-socket-source
      (s/consume (partial notify-clients! ws-conns) user-socket)
      )))


(defrecord Ws-Handler-Aleph-simple [ws-conns ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler (make-handler ws-conns)))
  ;; ws-handler holds a reference to the ws-conn (an atom) in a closure

  (stop [component] component))


;; TEST:
;; ws://localhost:5050/ws-simple
(comment 
  (require '[dev :refer [system]])
  (def user-conn (-> system :ws-conns-simple deref first))
  ((:cb user-conn) "Hi from server!")
  (s/put! (:socket user-conn) "Hi from server!")
  (doseq [client @cls]
    (s/put! client (str "hi there!")))
  )


