(ns nyse.api
  (:require [datomic.api :as d]
            [hildebrand.core :as hc]
            [taoensso.timbre :refer [debug info error spy]]
            [nyse.utils.datomic :as dtc-util]
            [nyse.utils.dynamo :as dyo-util]
            ))

;; (dev/reset)
;; -------------------------------------------------------------------------------
;; takes the state object!
;; has non pure functions
;; but is not stateful itself!

;; (require '[dev :refer [system db-conns reset]])
;;
;;
;; (add-order! (db-conns) {:ticker "goog"
;;                              :bid    44M
;;                              :offer  88M
;;                              :qty    23
;;                              })
;;
;; (find-orders (db-conns) "goog")
;; (dtc-util/get-entities-with-attr (:datomic (db-conns)) :order/symbol)
;; (dtc-util/get-entities-by-attr-val (d/db (:datomic (db-conns))) 
;;                                                    :order/symbol ;; attribute ..
;;                                                    "goog")

(defn add-order! [db-conns {:keys [ticker bid offer qty]}]

  ;; Datomic: returns transaction or error (sync)) 
  (d/transact (:datomic db-conns) [{:db/id        (d/tempid :db.part/user)
                                    :order/symbol ticker
                                    :order/bid    bid
                                    :order/offer  offer
                                    :order/qty    qty}])

  ;; Dynamo: returns channel immediately
  (hc/put-item!! (:dynamo db-conns) :orders {:id     (dyo-util/id) 
                                             :symbol ticker
                                             :bid    bid
                                             :offer  offer
                                             :qty    qty}))



(defn find-orders [dbs ticker]
  (-> {}
      (assoc :datomic (dtc-util/get-entities-by-attr-val (:datomic dbs)
                                                         :order/symbol ;; attribute ..
                                                         ticker)) ;; value combination to find 

      (assoc :dynamo (hc/query!! (:dynamo dbs) :orders {:symbol [:= ticker]} 
                                 {:sort :asc 
                                  :index :symbol
                                  :limit 10
                                  }))))
;; TODO: how to make this asyc?


(defn find-all-orders [dbs]
  (-> {}
      (assoc :datomic (dtc-util/get-entities-with-attr (:datomic dbs) :order/symbol))
      (assoc :dynamo  (hc/scan!! (:dynamo dbs) :orders {:limit 20}))))



