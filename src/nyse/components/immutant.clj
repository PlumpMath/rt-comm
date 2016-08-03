(ns nyse.components.immutant 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre :refer [log debug info spy]]
            [immutant.web :refer [run stop]]

            [nyse.utils.datomic :as utils]

            ))


(defrecord Immutant [conf handler1 server]
  component/Lifecycle

  (start [component]
    (info "Starting Immutant on port: " (:port conf) handler1)

    (->> (run (:handler handler1) :port (:port conf)) 
         (assoc component :server)))

  (stop [component]
    (stop (:server component))
    (assoc component :server nil)))


