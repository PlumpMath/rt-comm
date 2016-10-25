(ns rt-comm.components.immutant 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as l :refer [log debug info spy]]
            [immutant.web :as immut]))


(defrecord Immutant [conf handler server]
  component/Lifecycle

  (start [component]
    (if server 
      component ;; already started
      (do (info "Starting Immutant on port: " (:port conf))
          (->> (immut/run (:handler handler) :port (:port conf)) 
               (assoc component :server)))))

  (stop [component]
    (if server
      (do (try (immut/stop (:server component)) 
               (catch Throwable t
                 (l/error t "Error when stopping Immutant"))) 
          (assoc component :server nil))
      component))) ;; already closed!




