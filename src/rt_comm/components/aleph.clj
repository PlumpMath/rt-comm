(ns rt-comm.components.aleph 
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as l :refer [log debug info spy]]
            [aleph.http :refer [start-server]]))


(defrecord Aleph [conf handler server]
  component/Lifecycle

  (start [component]
    (if server 
      component ;; already started
      (do (info "Starting Aleph on port: " (:port conf))
          (->> (start-server (:handler handler) {:port (:port conf)}) 
               (assoc component :server)))))

  (stop [component]
    (if server
      (do (try (.close (:server component)) 
               (catch Throwable t
                 (l/error t "Error when stopping Aleph"))) 
          (assoc component :server nil))
      component))) ;; already closed!




