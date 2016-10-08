(ns rt-comm.utils.async-test
  (:require [clojure.core.match :refer [match]]

            [clojure.core.async :as a :refer [>!! <!!]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.async :refer :all]))



(deftest batch-rcv-ev-colls-test
  (let [c1 (a/chan 10)]
    (is (= (batch-rcv-ev-colls c1) nil) "Returns nil if no events were available")
    (>!! c1 [2 3 4]) 
    (>!! c1 [5 6 7])
    (is (= (batch-rcv-ev-colls c1) [2 3 4 5 6 7]) 
        "Rcv available event collections from ch and batch them into one event collection.")
    (is (= (batch-rcv-ev-colls c1) nil) "Returns nil if no events were available")))



(deftest pause-filter-keys-test
  (let [c1     (a/chan)
        res-ch (pause-filter-keys c1 :aa :bb)]
    (>!! c1 :cc)
    (>!! c1 [:dd "one"])
    (is (nil? (a/poll! res-ch)) "Will consume all msgs from ch until a match is found.")
    (>!! c1 [:bb "two"])
    (is (= (<!! res-ch) [:bb "two"]) "Returns a chan that will receive the first msg with a matching key.")))

;; (run-tests)

