(ns rt-comm.incoming-ws-user-pulsar-test
  (:require [clojure.core.match :refer [match]]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.components.event-queue :as event-queue]
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


;; (deftest take-all-or-wait-test
;;   (let [c1 (p/channel 10)]
;;
;;     (snd c1 [2 3 4])
;;     (snd c1 [5 6 7])
;;     (is (= (take-all-or-wait c1) [2 3 4 5 6 7])
;;         "Batch-receive all available event-colls and return instantly")
;;
;;     (let [fb (fiber (take-all-or-wait c1))]
;;       (is (= (deref fb 100 :timeout) :timeout)
;;           ".. or wait..")
;;       (snd c1 [2 3 4])
;;       (is (= @fb [2 3 4])
;;           ".. for next single event coll."))))
;;
;;
;; (deftest process-incoming!-test
;;   (let [c1 (p/channel 10)
;;         a1 (spawn #(receive))
;;         f1 (fiber (process-incoming! c1 #{:b :c} a1))]
;;
;;     (is (= (deref f1 100 :timeout) :timeout)
;;         "Wait for incoming")
;;
;;     (snd c1 [{:aa 1 :recip-chans #{:a :c}} 
;;              {:aa 2}])
;;
;;     (is (= (join a1) [:append! 
;;                       [{:aa 1, :recip-chans #{:b :a :c}} 
;;                        {:aa 2, :recip-chans #{:b :c}}]])
;;         "Consume, process and send msgs to event-queue.")))


(defsfn get-reset [ev-queue]
  (let [rep (spawn #(receive [:rcv x] x))]
    (sleep 200)
    (! ev-queue [:get-q-reset rep])
    (join rep)))

(defsfn get-process-cnt [incm-atr]
  (let [rep (spawn #(receive [:rcv x] x))]
    (sleep 200)
    (! incm-atr [:debug-prc-cnt rep])
    (join rep)))



(deftest incoming-ws-user-actor-test
  (let [in-ch    (p/channel 4 :displace)
        ev-queue (spawn event-queue/server-actor [] 100)
        incm-atr (spawn incoming-ws-user-actor 
                        in-ch ev-queue
                        {:batch-sample-intv 0})]

    (testing "Consumes msgs from in-ch - :append!s msgs to event-queue."
      (snd in-ch [{:aa 22} {:bb 24}])
      (snd in-ch [{:aa 26} {:bb 28}])
      (is (= @(spawn-fiber get-reset ev-queue) 
             [{:aa 22, :idx 0} {:bb 24, :idx 1} {:aa 26, :idx 2} {:bb 28, :idx 3}]) 
          "Msgs in sequence"))

    (testing "augment msgs based on settable state"
      (snd in-ch  [{:aa 12 :recip-chans #{:cc :dd}} 
                   {:bb 38}])
      (sleep 10)
      (! incm-atr [:set-fixed-recip-chs #{:ach :bch}]) ;; turn fixed receip-chans on
      (sleep 10)
      (snd in-ch  [{:aa 14 :recip-chans #{:bch :dd}} 
                   {:bb 40}])

      (is (= @(spawn-fiber get-reset ev-queue)
             [{:aa 12, :recip-chans #{:dd :cc}, :idx 0}
              {:bb 38, :idx 1}
              {:aa 14, :recip-chans #{:ach :dd :bch}, :idx 2}
              {:bb 40, :recip-chans #{:ach :bch}, :idx 3}]) 
          "Add chs after turned on.")

      (snd in-ch  [{:aa 16 :recip-chans #{:other}} 
                   {:bb 42}])
      (sleep 10)
      (! incm-atr [:set-fixed-recip-chs nil]) ;; turn fixed receip-chans off
      (sleep 10)
      (snd in-ch  [{:aa 18 :recip-chans #{:other}} 
                   {:bb 44}])

      (is (= @(spawn-fiber get-reset ev-queue)
             [{:aa 16, :recip-chans #{:ach :bch :other}, :idx 0}
              {:bb 42, :recip-chans #{:ach :bch}, :idx 1}
              {:aa 18, :recip-chans #{:other}, :idx 2}
              {:bb 44, :idx 3}]) 
          "allow to turn fixed receip-chans off"))

    (testing "pause/resume rcving msgs and let the windowed upstream ch overflow"
      (snd in-ch [{:aa 10} {:bb 20}])
      (snd in-ch [{:aa 11} {:bb 21}])
      (sleep 10)
      (! incm-atr :pause-rcv-overflow)
      (sleep 10)
      (snd in-ch [{:aa 12} {:bb 20}]) ;; should be dropped by displace channel 
      (snd in-ch [{:aa 13} {:bb 21}]) ;; should be dropped by displace channel
      (snd in-ch [{:aa 14} {:bb 20}]) ;; will be in buffer ..->
      (snd in-ch [{:aa 15} {:bb 21}])
      (snd in-ch [{:aa 16} {:bb 20}])
      (snd in-ch [{:aa 17} {:bb 21}])

      (is (= @(spawn-fiber get-reset ev-queue) 
             [{:aa 10, :idx 0} {:bb 20, :idx 1} 
              {:aa 11, :idx 2} {:bb 21, :idx 3}]) 
          "stoped processing msgs after :pause-rcv-overflow is called.")

      (sleep 10)
      (! incm-atr :resume-rcv)
      (sleep 10)

      (is (= @(spawn-fiber get-reset ev-queue) 
             [{:aa 14, :idx 0} {:bb 20, :idx 1} 
              {:aa 15, :idx 2} {:bb 21, :idx 3} 
              {:aa 16, :idx 4} {:bb 20, :idx 5} 
              {:aa 17, :idx 6} {:bb 21, :idx 7}]) 
          "process buffered msgs in in-ch after :resume-rcv is called."))

    (testing "Fast receiving" 
      (fiber (dotimes [n 32]
               (sleep 1)
               (snd in-ch [{:aa n}]))))

    (is (= (count @(spawn-fiber get-reset ev-queue)) 
           32) 
        "rcv all 32 msgs")

    ;; @(spawn-fiber get-reset ev-queue)

    ))


(deftest incoming-ws-user-actor-test2
  (let [in-ch    (p/channel 4 :displace)
        ev-queue (spawn event-queue/server-actor [] 100)
        incm-atr (spawn incoming-ws-user-actor 
                        in-ch ev-queue
                        {:batch-sample-intv 10})]

    (testing "batch incoming msgs using batch-sample-intv" 
      (fiber (dotimes [n 32]
               (sleep 4)
               (snd in-ch [{:aa n}]))))

    (is (= (count @(spawn-fiber get-reset ev-queue)) 
           32) 
        "rcv all 32 msgs")

    (let [prc-cnt @(spawn-fiber get-process-cnt incm-atr)] 
      (debug "process count:" prc-cnt)
      (is (and (> prc-cnt 14) (< prc-cnt 20)) 
          "batch 32 messages into 15-20 processing events - see also New events debug log."))

    ;; @(spawn-fiber get-reset ev-queue)
    ;; @(spawn-fiber get-process-cnt incm-atr)

    ))

;; (incoming-ws-user-actor-test2)
;; (info "---")
;;
;; (run-tests)



