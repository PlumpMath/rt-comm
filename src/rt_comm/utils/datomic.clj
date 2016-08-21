(ns rt-comm.utils.datomic
  (:require [datomic.api :as d]
            ;; [dev :require [system]]
            ))


(defn load-schema [path]
  (-> path slurp read-string))


(defn init-db [url schema-path]
  "Create and connect DB, transact schema, return connection."
  (d/create-database url)
  (let [schema (load-schema schema-path) 
        conn   (d/connect url)]
    (d/transact conn schema) ; install the schema in the db
    conn))


(defn entity [db id]
  (d/entity db id))


(defn touch [db results]
  "takes 'entity ids' results from a query
    e.g. '#{[272678883689461] [272678883689462] [272678883689459] [272678883689457]}'"
  (let [e (partial d/entity db)]
    (map #(-> % first e d/touch) results)))


(defn get-entities-by-attr-val [db attr a-val]
  "Returns entities which have an attribute - value fact."
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?attr ?val
         :where 
         [?e ?attr ?val]]
       db attr a-val))

(defn get-entities-with-attr [db attr]
  "Returns all entities which have attribute."
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?attr
         :where
         [?e ?attr]]
       db attr))


;; (defn all-entities-bak [conn]
;;   "Pull all entities from the db!"
;;   (d/q '[:find [(pull ?e [*]) ...] 
;;          :where 
;;          [?e :order/symbol]] 
;;        (d/db conn)))
;;
;;
;; (defn all-entities2 [conn]
;;   (let [db (d/db conn)]
;;     (->> (d/q '[:find ?e :where [?e :order/symbol]] db)
;;          (map first)
;;          (d/pull-many db '[*]))))
;;
;;
