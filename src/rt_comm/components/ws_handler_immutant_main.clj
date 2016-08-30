(ns rt-comm.components.ws-handler-immutant-main
  (:require [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]
            [taoensso.timbre :refer [debug info error spy]]
            ))


(defrecord Ws-Handler-Immutant-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler [] #_(make-handler ws-clients)))

  (stop [component] component))



