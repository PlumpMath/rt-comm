(ns rt-comm.components.aleph 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre :refer [log debug info spy]]
            [aleph.http :refer [start-server]]))


(defrecord Aleph [conf handler server]
  component/Lifecycle

  (start [component]
    (info "Starting Aleph on port: " (:port conf))

    (->> (start-server (:handler handler) {:port (:port conf)}) 
         (assoc component :server)))

  (stop [component]
    (.close (:server component))
    (assoc component :server nil)))

