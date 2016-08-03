(ns nyse.components.dynamo 
  (:require [com.stuartsierra.component :as component]
            [hildebrand.core :as hc]
            [hildebrand.channeled :as hch]

            [nyse.utils.dynamo :as utils]

            ))



(defrecord Dynamo [conf creds]
  component/Lifecycle

  (start [component]
    (let [creds (:local-creds conf)] 
      (utils/init-tables! creds (:table-specs conf)) 
      (assoc component :creds creds)))

  (stop [component] component))





