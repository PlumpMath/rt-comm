(ns rt-comm.components.datomic 
  (:require [com.stuartsierra.component :as component]

            [datomic.api     :as d]
            [taoensso.timbre :as timbre :refer [log debug info spy]]

            [rt-comm.utils.datomic :as utils]

            ))


(defrecord Datomic [conf conn]
  component/Lifecycle

  (start [component]
    (->> (utils/init-db (:uri conf) (:schema-path conf)) 
         (assoc component :conn)))

  (stop [component]
    (assoc component :conn nil)))


