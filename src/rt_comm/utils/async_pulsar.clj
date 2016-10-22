(ns rt-comm.utils.async-pulsar
  (:require [rt-comm.utils.utils :as utils :refer [valp]] 

            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [defsfn]]
            [co.paralleluniverse.pulsar.async :as pa]

            [clojure.core.match :refer [match]]

            [taoensso.timbre :refer [debug info error spy]])) 


(defsfn timeout! [p timeout v]
  "Suspend fiber for timeout ms, then deliver
  v to promise/deferred p."
  (do (p/sleep timeout) 
      (deliver p v))
  p)


(defn transform-ch [ch tx]
  "Returns pulsar channel with tx, reading form pulsar channel."
  (->> (pa/chan 1 tx) ;; Tx only has effect if buffer > 0
       (pa/pipe ch)))



(defn batch-rcv-ev-colls [ch]
  "Rcv available event collections from ch and batch
  them into one event collection. Returns nil if no events were available. Non-blocking."
  (loop [v []
         x (p/try-rcv ch)]
    (if-not x
      (valp v seq)  ;; return nil if empty
      (recur (into v x)
             (p/try-rcv ch)))))

;; TEST CODE:
;; (def c1 (p/channel 6))
;; (batch-rcv-ev-colls c1)
;; (snd c1 [2 3 4])
;; (snd c1 [5 6 7])


(defn rcv-rest [first-msg in-ch]
  "Rcv available msgs and append to first-msg vec. Never blocks."
  (->> (batch-rcv-ev-colls in-ch) ;; rcv other msgs or nil
       (into first-msg))) ;; into one vec of maps 


;; TEST CODE:
;; (def ch (p/channel 4))
;; (p/snd ch [{:aa 1}])
;; (p/snd ch [{:aa 2}])
;; (rcv-rest [{:aa 0}] ch)
;; (p/try-rcv ch)






