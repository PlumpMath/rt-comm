(ns rt-comm.components.websockets-aleph
  (:require [com.stuartsierra.component :as component] 
            [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [taoensso.timbre :refer [debug info error spy]]))


(defn connect! [connected-clients req-client-socket]
  (info "Add socket to connected-clients")
  (let [send-to-this-client-cb (partial s/put! req-client-socket)] 
    (swap! connected-clients conj req-client-socket))) ;; use CB here?

;; TODO: how to remove the callback from the set??
(defn disconnect! [connected-clients req-client-socket]
  (info "Remove socket from connected clients")
  (swap! connected-clients #(remove #{req-client-socket} %)))

(defn notify-clients! [connected-clients msg]
  (info "Broadcast message to all connected clients: " msg)
  (doseq [client-cb @connected-clients]
    (s/put! client-cb (str "From server: " msg))))


;; The ws-handler holds a write-/sink and a request/source function 
;; (received by the queue-component) - how to provide those? factory fn? protocol method?
;; to the event-queue or the queue itself in a closure

;; The ws-handler function is passed into the main-handler - via the Ws-Handler-.. component
;; it's attached to the /ws(-connect) route
;; It creates a user-socket (duplex-stream/source-and-sink) per user connection

;; may want to wait for a userID event
;; optionally maintain this ID - and socket connection
;; tag each message with client id.

;; The created user-socket:
;; - just store in "clients" atom for debugging - but don't actually use clients/make obsolete!
;; - user socket can just be consumed and fed into the queue-write/sink function
;;   - a function could be created on the first (auth/setup) message, which tags the message with a client/userID?
;;   - this id should also be stored in clients atom

;; why should I really need this pre-id ?? client can just always send it!!/?

;; the user socket needs a producer (incomming) and a consumer (outgoing) component
;; the consumer (outg) will be an actor which is pulling from queue via request/reply
;; consumer will only need a ref to the socket sink/write
;; consumer can be paused
;; consumer is also selective: will only send (or pull from queue/no?) messages the client is interested in

;; the producer (incoming) should combine several arriving messages (array of events)
;; into one message - producer will simply take-all from socket and integrate the queue/append!
;; producer might pause - buffer, disconnect
;; receive special command values - that will prefix/generate events. will commands enrich event data/async access DB?
;; will they just be passed into queue?
;; there was a question somewere: why not just store the commands??


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
      ;; (s/consume (partial notify-clients! connected-clients) user-socket)
      )))


(defrecord Ws-Handler-Aleph [clients ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler (make-handler clients)))
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




