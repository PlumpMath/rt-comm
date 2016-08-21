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

