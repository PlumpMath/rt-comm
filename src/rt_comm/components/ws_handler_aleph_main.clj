(ns rt-comm.components.ws-handler-aleph-main
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [taoensso.timbre :refer [debug info error spy]]))


(defn make-handler [ws-conns event-queue]
  "The returned handler will launch auth-init-actor on ws-connection."
  (fn [request]  ;; client requests a ws connection here
    
    (let [user-socket @(http/websocket-connection request)]
      user-socket
      ;; Add
      ;; (connect! ws-conns user-socket)

      ;; Remove
      ;; (s/on-closed user-socket #(disconnect! ws-conns user-socket))

      )))

(defrecord Ws-Handler-Aleph-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler (make-handler ws-conns event-queue)))

  (stop [component] component))



