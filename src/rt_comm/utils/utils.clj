(ns rt-comm.utils.utils
  (:require [clojure.edn :as edn]
            ))


(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})


(defn load-config [path]
  (-> path 
      slurp 
      edn/read-string))


(defn contains-el? [el coll] 
  (some #(= el %) coll))

;; TEST-CODE: 
;; (def user-data [{:user-id "pete" :pw "abc"} 
;;                 {:user-id "paul" :pw "cde"} 
;;                 {:user-id "mary" :pw "fgh"}])
;; (contains-el? {:user-id "mary" :pw "fgh"} user-data)


(defn valp
  "Returns v if predicate is true. Else returns nil or alt if provided."
  ([v pred] (when (pred v) v))
  ([v pred alt] (if (pred v) v alt)))

(defn fpred
  "Returns a fn that returns its arg, if pred is true. Else returns nil or alt if provided."
  ([pred]     (fn [a] (when (pred a) a)))
  ([pred alt] (fn [a] (if   (pred a) a alt))))

(defn cond= [m t-key t-val f]
  "Conditionally applies f to m if t-key of m = t-val."
  (if (= (t-key m) t-val)
    (f m)
    m))

;; TEST CODE:
;; (-> {:server :aleph :aa 123}
;;     (cond= :server :aleph #(assoc % :ch-incoming user-socket))
;;     )


(defn recent-items [cnt vect]
  "Return cnt recent items from vect. Nil if cnt not pos?"
  (some->> ((fpred pos?) cnt)
           (- (count vect)) ;; fetch idx
           (max 0)
           (subvec vect)))


(defn is-ev-coll? [v]
  "Returns true if v is a coll of maps, e.g. the first item is a map, nil otherwise."
  (some-> v (get 0) map?))


;; (defn add-to-col-in-table [rows column-key new-items]
;;   "Adds new-items to :column-key in rows [vec of maps]."
;;   (into [] (map (fn [row] 
;;                   (update row column-key 
;;                           #(into new-items %)))
;;                   ;; set new-items col-type as column val may be nil
;;                 rows)))

(defn add-to-col-in-table [rows column-key new-items]
  "Adds new-items to :column-key in rows [vec of maps].
  (add-to-col-in-table [{:aa [1]}] :aa [2 3])
  > [{:aa [2 3 1]}]"
  (if new-items 
    (into [] (map (fn [row] 
                    (update row column-key 
                            #(into new-items %)))
                  ;; set new-items col-type as column val may be nil
                  rows))
    rows)) ;; do nothing when new-items=nil


;; TEST CODE:
;; (add-to-col-in-table 
;;   [{:aa 1 :recip-chans [:a :c]} {:aa 2}]
;;   :recip-chans
;;   #{:d :c})



