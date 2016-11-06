(ns rt-comm.components.jetty 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as l :refer [log debug info spy]]
            [ring.adapter.jetty :as jetty]))


(defrecord Jetty [conf handler server]
  component/Lifecycle

  (start [component]
    (if server 
      component ;; already started
      (do (info "Starting Jetty on port: " (:port conf))
          (->> (jetty/run-jetty (:handler handler) {:port (:port conf)
                                                    :join? false}) 
               (assoc component :server)))))

  (stop [component]
    (if server
      (do (try (.stop (:server component)) 
               (catch Throwable t
                 (l/error t "Error when stopping Jetty"))) 
          (assoc component :server nil))
      component)))




