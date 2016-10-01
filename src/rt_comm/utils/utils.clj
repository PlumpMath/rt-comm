(ns rt-comm.utils.utils
  (:require [clojure.edn :as edn]
            ))

(defn load-config [path]
  (-> path 
      slurp 
      edn/read-string))


(defn valp
  "Returns v if predicate is true. Else returns nil or alt if provided."
  ([v pred] (when (pred v) v))
  ([v pred alt] (if (pred v) v alt)))

(defn fpred
  "Returns a fn that returns its arg, if pred is true. Else returns nil or alt if provided."
  ([pred]     (fn [a] (when (pred a) a)))
  ([pred alt] (fn [a] (if   (pred a) a alt))))


(defn recent-items [cnt vect]
  "Return cnt recent items from vect. Nil if cnt not pos?"
  (some->> ((fpred pos?) cnt)
           (- (count vect)) ;; fetch idx
           (max 0)
           (subvec vect)))


;; (defn add-to-col-in-table [rows column-key new-items]
;;   "Adds new-items to :column-key in rows [vec of maps]."
;;   (into [] (map (fn [row] 
;;                   (update row column-key 
;;                           #(into new-items %)))
;;                   ;; set new-items col-type as column val may be nil
;;                 rows)))

(defn add-to-col-in-table [rows column-key new-items]
  "Adds new-items to :column-key in rows [vec of maps]."
  (if new-items 
    (into [] (map (fn [row] 
                    (update row column-key 
                            #(into new-items %)))
                  ;; set new-items col-type as column val may be nil
                  rows))
    rows)) ;; do nothing when new-items=nil



;; TEST CODE:
;; (add-to-col-in-table 
;; [{:aa 1 :recip-chans [:a :c]} {:aa 2}]
;;   :recip-chans
;;   #{:d :c})




