(ns rt-comm.incoming-ws-user-pulsar-test
  (:require [clojure.core.match :refer [match]]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer [valp]]
            [rt-comm.incoming-ws-user-pulsar :refer :all]))


(deftest batch-rcv-ev-colls-test
  (let [c1 (p/channel 10)]
    (is (= (batch-rcv-ev-colls c1) nil) "Returns nil if no events were available")
    (snd c1 [2 3 4]) 
    (snd c1 [5 6 7])
    (is (= (batch-rcv-ev-colls c1) [2 3 4 5 6 7]) 
        "Rcv available event collections from ch and batch them into one event collection.")
    (is (= (batch-rcv-ev-colls c1) nil) "Returns nil if no events were available")))


(deftest take-all-or-wait-test
  (let [c1 (p/channel 10)]

    (snd c1 [2 3 4])
    (snd c1 [5 6 7])
    (is (= (take-all-or-wait c1) [2 3 4 5 6 7])
        "Batch-receive all available event-colls and return instantly")

    (let [fb (fiber (take-all-or-wait c1))]
      (is (= (deref fb 100 :timeout) :timeout)
          ".. or wait..")
      (snd c1 [2 3 4])
      (is (= @fb [2 3 4])
          ".. for next single event coll."))))


(deftest process-incoming!-test
  (let [c1 (p/channel 10)
        a1 (spawn #(receive))
        f1 (fiber (process-incoming! c1 #{:b :c} a1))]

    (is (= (deref f1 100 :timeout) :timeout)
        "Wait for incoming")

    (snd c1 [{:aa 1 :recip-chans #{:a :c}} 
             {:aa 2}])

    (is (= (join a1) [:append! 
                      [{:aa 1, :recip-chans #{:b :a :c}} 
                       {:aa 2, :recip-chans #{:b :c}}]])
        "Consume, process and send msgs to event-queue.")))


#_(run-tests)


