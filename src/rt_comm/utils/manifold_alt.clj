(ns rt-comm.utils.manifold-alt
  (:require [manifold.deferred :as d]))

;; Copied from https://github.com/ztellman/manifold/pull/102/files 


;; same technique as clojure.core.async/random-array
(defn- random-array [n]
  (let [a (int-array n)]
    (clojure.core/loop [i 1]
      (if (= i n)
        a
        (let [j (rand-int (inc i))]
          (aset a i (aget a j))
          (aset a j i)
          (recur (inc i)))))))

(defn alt'
  "Like `alt`, but only unwraps Manifold deferreds."
  [& vals]
  (let [d (d/deferred)
        cnt (count vals)
        ^ints idxs (random-array cnt)]
    (clojure.core/loop [i 0]
      (when (< i cnt)
        (let [i' (aget idxs i)
              x (nth vals i')]
          (if (d/deferred? x)
            (d/success-error-unrealized x
              val (d/success! d val)
              err (d/error! d err)
              (do (d/on-realized (d/chain' x)
                    #(d/success! d %)
                    #(d/error! d %))
                  (recur (inc i))))
            (d/success! d x)))))
    d))

(defn alt
  "Takes a list of values, some of which may be deferrable, and returns a
  deferred that will yield the value which was realized first.

    @(alt 1 2) => 1
    @(alt (future (Thread/sleep 1) 1)
          (future (Thread/sleep 1) 2)) => 1 or 2 depending on the thread scheduling

  Values appearing earlier in the input are preferred."
  [& vals]
  (->> vals
       (map #(or (d/->deferred % nil) %))
       (apply alt')))

