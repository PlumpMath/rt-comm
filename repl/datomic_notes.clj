(require 
  '[datomic.api :as d]
  '[dev :refer [system]]
  '[clojure.tools.namespace.repl :as tn]
  '[nyse.api :as api]
  '[nyse.utils.datomic :refer [touch]]
  )

(tn/disable-reload!)

(def conn (-> system :datomic :conn))

(-> system :handler1)
(:datomic :dynamo :immutant :handler1 :ws-handler :clients)
(identity system)
;; (def ddb (d/db conn))
;;
;;
(d/q '[:find ?qt
       :where 
       [?e :order/qty ?qt]
       ] 
     (d/db conn))
;;
;; (d/q '[:find ?va
;;        :where 
;;        [_ :order/symbol ?va]
;;        ] 
;;      (d/db conn))
;;
;;
;; ;; (first (d/datoms ddb :eavt))
;; ;; ;;
;; ;; ;; (d/q '[:find [(pull ?e '[*]) ...]
;; ;; ;;        :where 
;; ;; ;;        [?e :order/symbol]]
;; ;; ;;      ddb)
;; ;; ;;
;; ;; ;; (:order/symbol (d/entity ddb 17592186045418))
;; ;; ;;
;;
;; (api/find-all conn)
;; (all-entities2 conn)
;; [{:db/id 17592186045418, :order/symbol "appl", :order/bid 1717M, :order/qty 2211, :order/offer 77M}
;;  {:db/id 17592186045420, :order/symbol "goog", :order/bid 432M, :order/qty 2211, :order/offer 77M}
;;  {:db/id 17592186045422, :order/symbol "goog", :order/bid 11M, :order/qty 2211, :order/offer 66M}]


;; (defn all-entities2 [conn]
;;   (d/q '[:find [(pull ?e [*]) ...] 
;;          :where 
;;          [?e :order/symbol]] 
;;        (d/db conn)))
;;
;; (defn all-entities [conn]
;;   (let [db (d/db conn)]
;;     (->> (d/q '[:find ?e :where [?e :order/symbol]] db)
;;          (map first)
;;          (d/pull-many db '[*]))))



#_(->> (d/q '[:find ?e ;?bid
            :where 
            [?e :order/symbol]
            ;; [?e :order/bid ?bid]
            ] 
          (d/db conn))
     ;; (touch conn)
     (map first)
     ;; (map (partial entity-map (d/db conn)))

     (d/pull-many (d/db conn) '[*])

     (d/pull-many (d/db conn) [:order/symbol :order/bid])
     ;; ffirst
     ;; (entity-map conn)
     )

;; ;; ==>
;; [{:db/id        17592186045418,
;;   :order/symbol "appl",
;;   :order/bid    1717M,
;;   :order/qty    2211,
;;   :order/offer  77M}
;;  {:db/id        17592186045420,
;;   :order/symbol "goog",
;;   :order/bid    432M,
;;   :order/qty    2211,
;;   :order/offer  77M}
;;  {:db/id        17592186045422,
;;   :order/symbol "goog",
;;   :order/bid    11M,
;;   :order/qty    2211,
;;   :order/offer  66M}]
;;
;; #{[17592186045418 1717M] [17592186045420 432M] [17592186045422 11M]}
;; ;; ==>
;; #{[17592186045418 1717M] [17592186045420 432M] [17592186045422 11M]}
;; ;; ==>
;; [{:order/symbol "appl", :order/bid 1717M}
;;  {:order/symbol "goog", :order/bid 432M}
;;  {:order/symbol "goog", :order/bid 11M}]
;;
;;
;;
;; ;; ==>
;; ({:db/id 17592186045418, :order/symbol "appl", :order/bid 1717M, :order/qty 2211, :order/offer 77M}
;;  {:db/id 17592186045420, :order/symbol "goog", :order/bid 432M, :order/qty 2211, :order/offer 77M}
;;  {:db/id 17592186045422, :order/symbol "goog", :order/bid 11M, :order/qty 2211, :order/offer 66M})
;;
;; ;;
;; ;; {:db/id 17592186045419, 
;; ;;  :order/symbol "abc", 
;; ;;  :order/bid 1212M, 
;; ;;  :order/qty 99, 
;; ;;  :order/offer 2122M} 
;; ;; {:db/id 17592186045421, 
;; ;;  :order/symbol "TAA", 
;; ;;  :order/bid 22M, 
;; ;;  :order/qty 44, 
;; ;;  :order/offer 33M}
;; ;;
;; ;; #{[17592186045419] [17592186045421]}
;; ;;
;;
;; (defn entity-map [db entity-id] 
;;   (-> (d/entity db entity-id)
;;       d/touch
;;       ))
;; ;;
;; ;;
;; ;;
;; ;;
;; ;; (entity-map conn )
;; ;;
;; ;; (d/touch (d/entity (d/db conn) 17592186045421))
;; ;; {:db/id 17592186045421, 
;; ;;  :order/symbol "TAA", 
;; ;;  :order/bid 22M, 
;; ;;  :order/qty 44, 
;; ;;  :order/offer 33M}
;; ;;
;; ;; {:db/id 17592186045421}
;; ;;
;; ;; (d/touch {:db/id 17592186045421})
;; ;;
;; ;;
;; ;; {:db/id 17592186045419, 
;; ;;  :order/symbol "abc", 
;; ;;  :order/bid 1212M, 
;; ;;  :order/qty 99, 
;; ;;  :order/offer 2122M} 
;; ;;
;; ;; {:db/id 17592186045421, 
;; ;;  :order/symbol "TAA", 
;; ;;  :order/bid 22M, 
;; ;;  :order/qty 44, 
;; ;;  :order/offer 33M}
;; ;;
;; ;;
;; ;; ;;
;; ;; ;; #{[17592186045418] [17592186045420] [17592186045422]}
;; ;; ;; #{[17592186045418] [17592186045420]}
;; ;; ;; {:db/id        17592186045418,
;; ;; ;;  :order/symbol "TAB",
;; ;; ;;  :order/bid    99M,
;; ;; ;;  :order/qty    9866,
;; ;; ;;  :order/offer  9898M}
;; ;; ;;
;; ;; ;; (d/pull (d/db conn) '[*] 17592186045418)
;; ;; ;;
;; ;; ;; {:db/id 17592186045418, :order/symbol "TAB", :order/bid 99M, :order/qty 9866, :order/offer 9898M}
;; ;; ;; {:db/id 17592186045418}
;; ;; ;;
;; ;; ;; (touch conn #{[17592186045418]})
;; ;; ;;
;; ;; ;;
;; ;; ;;
;;
;;
;; (d/pull (d/db conn) '[*] 17592186045418)
;;
;;
;; ;; ;; ;; ==>
;; ;; ;; {:db/id        17592186045418,
;; ;; ;;  :order/symbol "TAA",
;; ;; ;;  :order/bid    22M,
;; ;; ;;  :order/qty    44,
;; ;; ;;  :order/offer  33M}
;; ;; ;;
;; ;; ;; (d/pull ddb '[*] (d/q '[:find [?va ...] :where [?va :order/symbol]] ddb))
;; ;; ;; (d/q '[:find [(d/pull ?va [:order/symbol :order/bid]) ...] :where [?va :order/symbol]] ddb)
;; ;; ;;
;; ;; ;;
;; ;; ;; ;; ==>
;; ;; ;; ["TAA" "TAB"]
;; ;; ;;
;; ;; ;; (d/q '[:find ?va .
;; ;; ;;        :where 
;; ;; ;;        [_ :order/symbol ?va]
;; ;; ;;        ] 
;; ;; ;;      ddb)
;; ;; ;; ;; ==>
;; ;; ;; "TAA"
;; ;; ;;
;; ;; ;;
;; ;; ;;
;; ;; ;; (d/pull ddb '[*] [:order/symbol "TAA"])
;; ;;
;; ;;
;; ;; (first (api/find-orders conn "abc"))
;; ;; {:db/id 17592186045419, 
;; ;;  :order/symbol "abc", 
;; ;;  :order/bid 1212M, 
;; ;;  :order/qty 99, 
;; ;;  :order/offer 2122M}
;; ;;
;; ;; ({:db/id 17592186045419, 
;; ;;   :order/symbol "TAA", 
;; ;;   :order/bid 991M, 
;; ;;   :order/qty 771, 
;; ;;   :order/offer 881M}
;; ;;  {:db/id 17592186045421, 
;; ;;   :order/symbol "TAA", 
;; ;;   :order/bid 1M, 
;; ;;   :order/qty 3, 
;; ;;   :order/offer 2M})
;; ;;
;; ;; ;; [ticker bid offer qty]
;; ;; (api/add-order conn {:ticker "abc"
;; ;;                      :bid (bigdec 1212)
;; ;;                      :offer (bigdec 2122)
;; ;;                      :qty 99})
;; ;;
;; ;; (api/find-all conn)
;; ;;
;; ;; ({:db/id 17592186045419, :order/symbol "abc", :order/bid 1212M, :order/qty 99, :order/offer 2122M}
;; ;;  {:db/id 17592186045421, :order/symbol "TAA", :order/bid 22M, :order/qty 44, :order/offer 33M})
;; ;;
;; ;; ({:db/id 17592186045419, 
;; ;;   :order/symbol "abc", 
;; ;;   :order/bid 1212M, 
;; ;;   :order/qty 99, 
;; ;;   :order/offer 2122M} 
;; ;;  {:db/id 17592186045421, 
;; ;;   :order/symbol "TAA", 
;; ;;   :order/bid 22M, 
;; ;;   :order/qty 44, 
;; ;;   :order/offer 33M})
;; ;;
;; ;;
;; ;;
