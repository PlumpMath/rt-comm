(ns rt-comm.utils.async
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 

            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!! alt! pipe
                                              sliding-buffer]]

            [manifold.stream :as s]
            [co.paralleluniverse.pulsar.async :as pa]

            [clojure.core.match :refer [match]]

            [taoensso.timbre :refer [debug info error spy]])) 



(defn transform-ch [ch tx]
  "Apply tx to ch."
  (->> (chan 1 tx) ;; Tx only has effect if buffer > 0
       (a/pipe ch)))

(defn transf-st-ch [stream tx]
  "Returns ch with tx, reading form Manifold stream."
  (->> (chan 1 tx) ;; Tx only has effect if buffer > 0
       (s/connect stream)))

(defn transf-st-pch [stream tx]
  "Returns pulsar channel with tx, reading form Manifold stream."
  (->> (pa/chan 1 tx) ;; Tx only has effect if buffer > 0
       (s/connect stream)))


(defn batch-rcv-ev-colls [ch]
  "Poll! available event collections from ch and batch
  them into one event collection. Returns nil if no events were available. Non-blocking."
  (loop [v []
            x (a/poll! ch)]
           (if-not x
             (valp v seq)  ;; return nil if empty
             (recur (into v x)
                    (a/poll! ch)))))

;; TEST CODE:
;; (def c1 (a/chan 6))
;; (<!! (batch-rcv-ev-colls c1))
;; (>!! c1 [2 3 4])
;; (>!! c1 [5 6 7])
;; (vector? )


(defn rcv-rest [first-msg ch]
  "Rcv available msgs and append to first-msg vec. Never blocks."
  (->> (batch-rcv-ev-colls ch) ;; rcv other msgs or nil
       (into first-msg))) ;; into one vec of maps 

;; TEST CODE:
;; (def ch (chan 4))
;; (>!! ch [{:aa 1}])
;; (>!! ch [{:aa 2}])
;; (rcv-rest [{:aa 0}] ch)
;; (poll! ch)


(defn pause-filter-keys [ch & msg-keys] 
  "Returns a chan that will receive the first msg with
  a matching key. Will consume all msgs from ch until a match is found."
  (let [valid-k? (set msg-keys)] 
    (go-loop [] 
             (let [msg (<!! ch)]
               (cond
                 (some-> msg (valp vector?) first valid-k?) msg
                 (valid-k? msg) msg
                 :else (recur))))))

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







