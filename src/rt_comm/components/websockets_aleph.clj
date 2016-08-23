(ns rt-comm.components.websockets-aleph
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [taoensso.timbre :refer [debug info error spy]]))


(defn connect! [connected-clients req-client-socket]
  (info "Add socket to connected-clients")
  (swap! connected-clients conj req-client-socket))

(defn disconnect! [connected-clients req-client-socket]
  (info "Remove socket from connected clients")
  (swap! connected-clients #(remove #{req-client-socket} %)))

(defn notify-clients! [connected-clients msg]
  (info "Broadcast message to all connected clients: " msg)
  (doseq [client-socket @connected-clients]
    (s/put! client-socket (str "From server: " msg))))


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
      ;; Message
      (s/consume (partial notify-clients! connected-clients) user-socket)
      )))


(defrecord Ws-Handler-Aleph [clients handler]
  component/Lifecycle

  (start [component]
    (assoc component :handler (make-handler clients)))
  ;; the handler holds a reference to the state (an atom) in a closure
  ;; ws-handler therefore contains a stateful reference
  ;; ws-handler is passed into other components

  (stop [component] component))


;; TEST:
;; ws://localhost:4242/ws 
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




