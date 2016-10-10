(ns rt-comm.utils.async-manifold
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 

            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.time :as t]

            [clojure.core.match :refer [match]]

            [taoensso.timbre :refer [debug info error spy]])) 



(defn batch-rcv-ev-colls [in-stream]
  "Try-take! available event collections from in-steam and batch
  them into one event collection. Returns nil if no events were available. Non-blocking."
  (loop [v []
         x @(s/try-take! in-stream 0)]
    (if-not x
      (valp v seq)  ;; return nil if empty
      (recur (into v x)
             @(s/try-take! in-stream 0)))))

;; TEST CODE:
;; (def s1 (s/stream 6))
;; (batch-rcv-ev-colls s1)
;; (s/put! s1 [2 3 4])
;; (s/put! s1 [5 6 7])


(defn rcv-rest [first-msg ch]
  "Rcv available msgs and append to first-msg vec. Never blocks."
  (->> (batch-rcv-ev-colls ch) ;; rcv other msgs or nil
       (into first-msg))) ;; into one vec of maps 

;; TEST CODE:
;; (def ch (s/stream 4))
;; (>!! ch [{:aa 1}])
;; (>!! ch [{:aa 2}])
;; (rcv-rest [{:aa 0}] ch)
;; (s/try-take! ch)

;; ->>>> TODO:
;; (defn pause-filter-keys [ch & msg-keys] 
;;   "Returns a chan that will receive the first msg with
;;   a matching key. Will consume all msgs from ch until a match is found."
;;   (let [valid-k? (set msg-keys)] 
;;     (go-loop [] 
;;              (let [msg (<!! ch)]
;;                (cond
;;                  (some-> msg (valp vector?) first valid-k?) msg
;;                  (valid-k? msg) msg
;;                  :else (recur))))))

;; TEST CODE:
;; (def c1 (chan))
;; (def res (pause-filter-keys c1 :aa :bb))
;; (future (info (<!! res)))
;; (>!! c1 :uu)
;; (>!! c1 [:vv "eins"])
;; (>!! c1 [:bb "eins"])
;; (>!! c1 :aa)
;; (future (>!! c1 [:bb "zwei"])) 
;; (info (<!! c1)) ;; works again as normal!


