(ns rt-comm.components.ws-handler-immutant-simple
  (:require [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]
            [taoensso.timbre :refer [debug info error spy]]
            ))

(defn connect! [ws-conns req-client-socket]
  (info "Add socket to ws-conns" ws-conns req-client-socket) ;; TODO: validate args
  (let [send-to-this-client-cb (partial async/send! req-client-socket)] 
    (swap! ws-conns conj {:socket req-client-socket :cb send-to-this-client-cb})))

(defn disconnect! [ws-conns req-client-socket {:keys [code reason]}]
  (info "Remove socket from connected clients" code "reason:" reason)
  (swap! ws-conns #(remove (comp #{req-client-socket} :socket) %)))

(defn notify-clients! [ws-conns req-client-socket msg]
  (info "Broadcast message to all connected clients: " msg)
  (doseq [client @ws-conns]
    ((:cb client) (str "From server: " msg))))

(defn make-handler [ws-conns]
  (fn [request]  ;; client requests a ws connection here
    (async/as-channel
      request
      {:on-open    (partial connect! ws-conns)
       :on-close   (partial disconnect! ws-conns) 
       :on-message (partial notify-clients! ws-conns)}))) ;; client messages don't come from the connection socket, but from this callback


(defrecord Ws-Handler-Immutant-simple [ws-conns ws-handler]
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
  (async/send! (:socket user-conn) "Hi from server!")
  (doseq [client @cls]
    (async/send! client (str "hi there!")))
  )
