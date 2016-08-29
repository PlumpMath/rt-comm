(ns rt-comm.components.websockets-immutant
  (:require [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]
            [taoensso.timbre :refer [debug info error spy]]
            ))

(defn connect! [connected-clients req-client-socket]
  (info "Add socket to connected-clients")
  (let [send-to-this-client-cb (partial async/send! req-client-socket)] 
    (swap! connected-clients conj send-to-this-client-cb)))

(defn disconnect! [connected-clients req-client-ch {:keys [code reason]}]
  (info "Remove socket from connected clients" code "reason:" reason)
  (swap! connected-clients #(remove #{req-client-ch} %)))

(defn notify-clients! [connected-clients req-client-socket msg]
  (info "Broadcast message to all connected clients: " msg)
  (doseq [client-cb @connected-clients]
    (client-cb (str "From server: " msg))))

(defn make-handler [clients]
  (fn [request]  ;; client requests a ws connection here
    (async/as-channel
      request
      {:on-open    (partial connect! clients)
       :on-close   (partial disconnect! clients) 
       :on-message (partial notify-clients! clients)}))) ;; client messages don't come from the connection socket, but from this callback


(defrecord Ws-Handler-Immutant [clients ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler (make-handler clients)))
  ;; the handler holds a reference to the state (an atom) in a closure
  ;; ws-handler therefore contains a stateful reference
  ;; ws-handler is passed into other components

  (stop [component] component))


;; TEST:
;; ws://localhost:4242/ws 

;; (require '[dev :refer [system]])
;; (def cls (-> system :ws-handler :clients))
;;
;; (doseq [client @cls]
;;     (async/send! client (str "hi there!")))
;;
;; (-> @(-> system :clients)
;;     vec
;;     (get 0)
;;     (async/send! "Tee")
;;     )
;;
;; (def co @(-> system :clients))
;;
;; (def co1 (first co))
;;
;; (type co1)
;;
;; (def cf (partial async/send! co1))
;;
;; (cf "zwei")


