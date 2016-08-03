(require 
  '[dev :refer [system db-conns reset]]
  '[nyse.api :refer [add-order! find-orders find-all-orders]]
  '[nyse.utils.datomic :as dtc-util]
  '[nyse.utils.dynamo  :as dyo-util]
  '[nyse.utils.logging :as logging]

  '[taoensso.timbre :refer [debug info error spy]]
  '[datomic.api :as d]
  '[hildebrand.core :as hc]
  '[hildebrand.channeled :as hch]
  '[clojure.tools.namespace.repl :as tn]
  '[clojure.core.async :as a :refer [chan <! >! go-loop go alt!! 
                                     <!! >!!
                                     close! put! take! thread timeout
                                     offer! poll! promise-chan
                                     sliding-buffer]])

Hi! I would like to return the value of an expression if that value meets a predicate, otherwise I want to return a default value. Using a `let` and an `if` does the job:
(def cm [{:index 3} {:index 4}])

(let [v (-> cm last :index)]
  (if (integer? v) v -1))

()


(let [ei #(when (%1 %2) %2)]
  (ei 23 integer?))

(let [ei (fn [v pred] (when (pred v) v))]
  (ei :23 integer?))


(#(when (%1 %2) %2) )

(when (integer? 5) 5)

(-> (-> cm last :index) (valp integer? -1))

(exp-if (-> cm last :index) integer? -1)

(defn valp
  "Returns v if predicate is true. Else returns nil or alt if provided."
  ([v pred] (when (pred v) v))
  ([v pred alt] (if (pred v) v alt)))

(defn fpred [pred] (fn [a] (if (pred a) a)))

(fn novel-evts [queue cur-idx req-idx]
  (let [cnt-new-evts (- cur-idx req-idx) ;; may be 0
        fetch-idx    (- (count queue) cnt-new-evts) ;; may be = count
        ]
    (subvec queue fetch-index))) ;; may be = []

(fn novel-evts [queue cur-idx req-idx]
  (let [cnt-new-evts (- cur-idx req-idx) ;; may be 0
        fetch-idx    (- (count queue) cnt-new-evts) ;; may be = count
        ]
    (subvec queue fetch-index))) ;; may be = []


(valp 0 (complement zero?))
((fpred (complement zero?)) 0)

(defn recent-items [vect cnt]
  "Return cnt recent items from vect. Nil if cnt not pos?"
  (some->> ((fpred pos?) cnt)
           (- (count queue)) ;; fetch idx
           (subvec vect)))


(fn novel-evts [queue cur-idx req-idx]
  (let [cnt-new-evts (- cur-idx req-idx) ;; may be 0
        fetch-idx    (- (count queue) cnt-new-evts) ;; may be = count
        ]
    (subvec queue fetch-index))) ;; may be = []



wenn cnt > 0 dann fetch else nil

(defn latest-index [init-events] 
  "Latest :index val in event-queue data, or -1 if not found."
  (let [v (-> init-events last :index)] 
    (if (integer? v) v -1)))


(if-some [a 10] :true :false)

(when-some [x false] {:x x})

(when )

(let [])
(some-> {:a 1} 
        :b 
        inc)

(condp some [1 2 3 4]
  #{0 6 7} :>> inc
  #{4 5 9} :>> dec
  #{1 2 3} :>> #(+ % 3))

(some #{4 5 9} [1 2 3 4])

(defn pos-neg-or-zero [n]
  (cond
    (< n 0) "negative"
    (> n 0) "positive"
    :else "zero"))

(let [mystr "hello"]
  (case mystr
    "" 0
    "hello" (count mystr)))


(require '[clojure.reflect :as r])
(use '[clojure.pprint :only [print-table]])

(print-table (:members (r/reflect ac1)))


(reduce-kv #(assoc %1 %2 %3) {} {:a 1 :b 2 :c 3})

(reduce-kv (fn [accum k v]
             (assoc accum k (inc v)))
           {} 
           {:a 1 :b 2 :c 3})

(merge {:index 32} {:a 1 :b 2 :c 3})

(def bb 
  [{:game 1 :won true} 
   {:game 2 :won false} 
   {:game 3 :won true} 
   {:game 4 :won true}])

[{:game 1, :won true, :total 1, :streak 1}
 {:game 2, :won false, :total 1, :streak 0}
 {:game 3, :won true, :total 2, :streak 1}
 {:game 4, :won true, :total 3, :streak 2}]

(loop [idx 4
       source bb
       res []]
  (assoc rec))

(reduce (fn [ac item]
          (conj ac (assoc item :new "hi")))
        []
        bb)

(reduce-kv
  (fn [result index game]
    (let [last-game (last result)
          wins (if (:won game) 
                 (inc (:total last-game 0)) 
                 (:total last-game))
          streak (if (:won game) 
                   (inc (:streak last-game 0)) 
                   0)]
      (println (assoc game :total wins :streak streak))
      (conj result (assoc game :total wins :streak streak))))
  []
  all-games)

(def vector-of-maps [{:a 1 :b 2} {:a 3 :b 4}])

(defn update-map [m f] 
  (reduce-kv (fn [m k v] 
               (assoc m k (f v))) {} m))

(map #(update-map % inc) vector-of-maps)


=> ({:b 3, :a 2} {:b 5, :a 4})

(def products {:phone 200 :tv 300 :pc 100 :xbox 150})

;; we want to sum the prices only for :phone, :tv and :xbox. To do that we can write something like this:

(reduce-kv (fn [s k v]
             (if (#{:phone :tv :xbox} k)
               (+ s v) s))
           0 products)

(def numbers (vec (range 10)))
=> [0 1 2 3 4 5 6 7 8 9]

(reduce-kv (fn [s k v]
             (if (even? k) (+ s v) s))
           0 numbers)
(assoc :b1 {:aa 23})
(-> dev/system :immutant :server)
(-> dev/system :handler1 :ws-handler)
(def conn  (-> dev/system :immutant))

(def conn  (-> dev/system :datomic :conn))
(def creds (-> dev/system :dynamo :creds))

(add-order! (db-conns) {:ticker "goog"
                             :bid    44M
                             :offer  88M
                             :qty    23
                             })

(-> (find-all-orders (db-conns))
    logging/browse)
(db-conns)
(find-all-orders (db-conns))
;; ==>
{:datomic [],
 :dynamo
 ({:offer 111N, :symbol "TAA", :id 35037N, :bid 22N, :qty 44N}
  {:offer 11N, :symbol "TAA", :id 35036N, :bid 22N, :qty 44N}
  {:offer 36N, :symbol "TAA", :id 35034N, :bid 22N, :qty 44N}
  {:offer 34N, :symbol "TAA", :id 35031N, :bid 22N, :qty 44N}
  {:offer 33N, :symbol "TAA", :id 34806N, :bid 22N, :qty 44N}
  {:offer 33N, :symbol "TAA", :id 34819N, :bid 22N, :qty 44N}
  {:offer 30N, :symbol "TAA", :id 35033N, :bid 22N, :qty 44N}
  {:offer 31N, :symbol "TAA", :id 35030N, :bid 22N, :qty 44N})}
;; ==>
{:datomic
 [{:db/id        17592186045419,
   :order/symbol "TAA",
   :order/bid    22M,
   :order/qty    44,
   :order/offer  33M}
  {:db/id        17592186045421,
   :order/symbol "TAA",
   :order/bid    22M,
   :order/qty    44,
   :order/offer  33M}],
 :dynamo
 ({:offer        33N, :symbol "TAA", :id 34806N, :bid 22N, :qty 44N}
  {:offer        33N, :symbol "TAA", :id 34819N, :bid 22N, :qty 44N})}

(hc/query!! creds :orders {:id [:= 34806N]} {:limit 10})
(hc/query!! creds :orders {:symbol [:= "TAA"]} {:index :symbol
                                                :limit 10})

(hc/query!! creds :orders {:symbol [:= "TA"]} 
                                  {
                                   :filter [:> [:offer] 20]
                                   :sort  :desc
                                   :index :symbol   
                                   :limit 10
                                   })
;; ==>
({:offer 111N, :symbol "TA", :id 35856N, :bid 22N, :qty 44N}
 {:offer 33N, :symbol "TA", :id 35831N, :bid 22N, :qty 44N}
 {:offer 22N, :symbol "TA", :id 35828N, :bid 22N, :qty 44N})
;; ==>
()
;; ==>
({:offer 111N, :symbol "TA", :id 35856N, :bid 22N, :qty 44N}
 {:offer 33N, :symbol "TA", :id 35831N, :bid 22N, :qty 44N}
 {:offer 22N, :symbol "TA", :id 35828N, :bid 22N, :qty 44N}
 {:offer 12N, :symbol "TA", :id 35866N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35830N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35829N, :bid 22N, :qty 44N}
 {:offer 2N, :symbol "TA", :id 35845N, :bid 22N, :qty 44N})
;; ==>
({:offer 2N, :symbol "TA", :id 35845N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35829N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35830N, :bid 22N, :qty 44N}
 {:offer 12N, :symbol "TA", :id 35866N, :bid 22N, :qty 44N}
 {:offer 22N, :symbol "TA", :id 35828N, :bid 22N, :qty 44N}
 {:offer 33N, :symbol "TA", :id 35831N, :bid 22N, :qty 44N}
 {:offer 111N, :symbol "TA", :id 35856N, :bid 22N, :qty 44N})
;; ==>
({:offer 2N, :symbol "TA", :id 35845N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35829N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35830N, :bid 22N, :qty 44N}
 {:offer 22N, :symbol "TA", :id 35828N, :bid 22N, :qty 44N}
 {:offer 33N, :symbol "TA", :id 35831N, :bid 22N, :qty 44N}
 {:offer 111N, :symbol "TA", :id 35856N, :bid 22N, :qty 44N})
;; ==>
({:offer 2N, :symbol "TA", :id 35845N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35829N, :bid 22N, :qty 44N}
 {:offer 11N, :symbol "TA", :id 35830N, :bid 22N, :qty 44N}
 {:offer 22N, :symbol "TA", :id 35828N, :bid 22N, :qty 44N}
 {:offer 33N, :symbol "TA", :id 35831N, :bid 22N, :qty 44N}
 {:offer 111N, :symbol "TA", :id 35856N, :bid 22N, :qty 44N})
;; ==>
({:offer 112N, :symbol "TA", :id 35445N, :bid 22N, :qty 44N}
 {:offer 2233N, :symbol "TA", :id 35556N, :bid 22N, :qty 44N}
 {:offer 44N, :symbol "TA", :id 35778N, :bid 22N, :qty 44N})


;; ==>
({:offer 2233N, :symbol "TA", :id 35556N, :bid 22N, :qty 44N})


(dtc-util/get-entities-by-attr-val conn :order/symbol "appl")
(dtc-util/get-entities-with-attr   conn :order/symbol)
;; ==>
[{:db/id        17592186045419,
  :order/symbol "goog",
  :order/bid    123M,
  :order/qty    88,
  :order/offer  77M}
 {:db/id        17592186045421,
  :order/symbol "WER",
  :order/bid    22M,
  :order/qty    61,
  :order/offer  51M}
 {:db/id        17592186045423,
  :order/symbol "goog",
  :order/bid    44M,
  :order/qty    23,
  :order/offer  88M}]

(hc/get-item!! creds :orders {:symbol "TAA"})

(hc/list-tables!! (-> dev/system :dynamo :creds))


;; -------------------------------------------------------------------------------

