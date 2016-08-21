(ns rt-comm.utils.dynamo
  (:require
    [hildebrand.core :as hc]
    [hildebrand.channeled :as hch]
    [taoensso.timbre :refer [debug info error spy]]
    [clojure.core.async :as a :refer [chan <! >! go-loop go alt!! 
                                      <!! >!!
                                      close! put! take! thread timeout
                                      offer! poll! promise-chan
                                      sliding-buffer]]    
    ))


(defn id [] (-> (gensym) str (subs 3) read-string))
;; TODO: not sure how unique this id is


(defn load-table-spec [path]
  (-> path slurp read-string))


(defn init-tables! [creds tables-conf]
  (doseq [table tables-conf]
    (->> (load-table-spec (:spec-path table))
         (hc/ensure-table!! creds)))

  (let [tables (hc/list-tables!! creds)] 
    (if (seq tables)
      (info  (str "DynamoDB tables: " tables))
      (error (str "No DynamoDB tables created for spec: " tables-conf)))))


(defn delete-all-tables!! [creds]
  (doseq [t-name (hc/list-tables!! creds)] 
    (hc/delete-table!! creds t-name)))



