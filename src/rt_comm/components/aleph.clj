(ns rt-comm.components.aleph 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre :refer [log debug info spy]]
            [aleph.http :refer [start-server]]))


(defrecord Aleph [conf handler1 server]
  component/Lifecycle

  (start [component]
    (info "Starting Aleph on port: " (:port conf) handler1)

    (->> (start-server handler1 {:port (:port conf)}) 
         (assoc component :server)))

  (stop [component]
    (.close (:server component))
    (assoc component :server nil)))

