(ns nyse.components.websockets
  (:require [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]
            [taoensso.timbre :refer [debug info error spy]]
            ))

;; TEST:
;; ws://localhost:4242/ws 
;; (require '[dev :refer [system]])
;; (def cls (-> system :ws-handler :clients))
;;
;; (doseq [client @cls]
;;     (async/send! client (str "hi there!")))
;;
;; (-> @(-> system :ws-handler :clients)
;;     vec
;;     (get 1)
;;     (async/send! "Tee")
;;     )

(defn connect! [clients req-client-ch]
  (info "channel open")
  (swap! clients conj req-client-ch))

(defn disconnect! [clients req-client-ch {:keys [code reason]}]
  (info "channel close code:" code "reason:" reason)
  (swap! clients #(remove #{req-client-ch} %)))

(defn notify-clients! [clients req-client-ch msg]
  (info "broadcast: " msg)
  (doseq [client @clients]
    (async/send! client (str "pp:" msg))))

(defn make-handler [clients]
  (fn [request]  ;; client requests a ws connection here
    (async/as-channel
      request
      {:on-open    (partial connect! clients)
       :on-close   (partial disconnect! clients) 
       :on-message (partial notify-clients! clients)})))


(defrecord Ws-Handler [clients handler]
  component/Lifecycle

  (start [component]
    (assoc component :handler (make-handler clients)))
  ;; the handler holds a reference to the state (an atom) in a closure
  ;; ws-handler therefore contains a stateful reference
  ;; ws-handler is passed into other components

  (stop [component] component))



