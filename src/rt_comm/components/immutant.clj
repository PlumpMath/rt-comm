(ns rt-comm.components.immutant 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre :refer [log debug info spy]]
            [immutant.web :refer [run stop]]))


(defrecord Immutant [conf handler server]
  component/Lifecycle

  (start [component]
    (info "Starting Immutant on port: " (:port conf))
    (info "IMMUT handler!!" handler)

    (->> (run (:handler handler) :port (:port conf)) 
         (assoc component :server)))

  (stop [component]
    (stop (:server component))
    (assoc component :server nil)))


