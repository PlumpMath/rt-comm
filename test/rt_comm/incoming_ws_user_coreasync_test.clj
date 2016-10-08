(ns rt-comm.incoming-ws-user-coreasync-test
  (:require [clojure.core.match :refer [match]]

            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!! alt! pipe
                                              close! put! take! thread timeout
                                              offer! poll! promise-chan
                                              sliding-buffer]]

            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox mailbox-of whereis 
                                                       register! unregister! self]]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]


            [clojure.test :as t :refer [is are run-all-tests run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.components.event-queue :as event-queue]
            [rt-comm.utils.utils :refer [valp]]

            [rt-comm.incoming-ws-user-coreasync :refer :all]

            ))



(defsfn get-reset [ev-queue]
  (let [rep (spawn #(receive [:rcv x] x))]
    (sleep 200)
    (! ev-queue [:get-q-reset rep])
    (join rep)))

(defn get-process-cnt [cmd-ch]
  (go (<! (timeout 200)) ;; wait for previous asyc op to complete
      (let [res (promise)]
        (>!! cmd-ch [:debug-prc-cnt res])
        @res)))



(deftest incoming-ws-user-actor-test
  (let [in-ch    (a/chan (sliding-buffer 4))
        ev-queue (spawn event-queue/server-actor [] 100)
        cmd-ch   (a/chan) 
        incm-atr (incoming-ws-user-actor 
                   in-ch #(! ev-queue %) cmd-ch 
                   {:batch-sample-intv 0})]

    (testing "Consumes msgs from in-ch - :append!s msgs to event-queue."
      (>!! in-ch [{:aa 22} {:bb 24}])
      (>!! in-ch [{:aa 26} {:bb 28}])
      (is (= @(spawn-fiber get-reset ev-queue) 
             [{:aa 22, :idx 0} {:bb 24, :idx 1} {:aa 26, :idx 2} {:bb 28, :idx 3}]) 
          "Msgs in sequence"))

    (testing "augment msgs based on settable state"
      (>!! in-ch  [{:aa 12 :recip-chans #{:cc :dd}} 
                   {:bb 38}])
      (sleep 10)
      (>!! cmd-ch [:set-fixed-recip-chs #{:ach :bch}]) ;; turn fixed receip-chans on
      (sleep 10)
      (>!! in-ch  [{:aa 14 :recip-chans #{:bch :dd}} 
                   {:bb 40}])

      (is (= @(spawn-fiber get-reset ev-queue)
             [{:aa 12, :recip-chans #{:dd :cc}, :idx 0}
              {:bb 38, :idx 1}
              {:aa 14, :recip-chans #{:ach :dd :bch}, :idx 2}
              {:bb 40, :recip-chans #{:ach :bch}, :idx 3}]) 
          "Add chs after turned on.")

      (>!! in-ch  [{:aa 16 :recip-chans #{:other}} 
                   {:bb 42}])
      (sleep 10)
      (>!! cmd-ch [:set-fixed-recip-chs nil]) ;; turn fixed receip-chans off
      (sleep 10)
      (>!! in-ch  [{:aa 18 :recip-chans #{:other}} 
                   {:bb 44}])

      (is (= @(spawn-fiber get-reset ev-queue)
             [{:aa 16, :recip-chans #{:ach :bch :other}, :idx 0}
              {:bb 42, :recip-chans #{:ach :bch}, :idx 1}
              {:aa 18, :recip-chans #{:other}, :idx 2}
              {:bb 44, :idx 3}]) 
          "allow to turn fixed receip-chans off"))

    (testing "pause/resume rcving msgs and let the windowed upstream ch overflow"
      (>!! in-ch [{:aa 10} {:bb 20}])
      (>!! in-ch [{:aa 11} {:bb 21}])
      (sleep 10)
      (>!! cmd-ch :pause-rcv-overflow)
      (sleep 10)
      (>!! in-ch [{:aa 12} {:bb 20}]) ;; should be dropped by displace channel 
      (>!! in-ch [{:aa 13} {:bb 21}]) ;; should be dropped by displace channel
      (>!! in-ch [{:aa 14} {:bb 20}]) ;; will be in buffer ..->
      (>!! in-ch [{:aa 15} {:bb 21}])
      (>!! in-ch [{:aa 16} {:bb 20}])
      (>!! in-ch [{:aa 17} {:bb 21}])

      (is (= @(spawn-fiber get-reset ev-queue) 
             [{:aa 10, :idx 0} {:bb 20, :idx 1} 
              {:aa 11, :idx 2} {:bb 21, :idx 3}]) 
          "stoped processing msgs after :pause-rcv-overflow is called.")

      (sleep 10)
      (>!! cmd-ch :resume-rcv)
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
               (>!! in-ch [{:aa n}]))))

    (is (= (count @(spawn-fiber get-reset ev-queue)) 
           32) 
        "rcv all 32 msgs")

    ;; @(spawn-fiber get-reset ev-queue)

    ))


(deftest incoming-ws-user-actor-test2
  (let [in-ch    (a/chan (sliding-buffer 4))
        ev-queue (spawn event-queue/server-actor [] 100)
        cmd-ch   (a/chan) 
        incm-atr (incoming-ws-user-actor 
                   in-ch #(! ev-queue %) cmd-ch 
                   {:batch-sample-intv 10})] 

    (testing "batch incoming msgs using batch-sample-intv" 
      (go-loop [x 0] 
         (<! (timeout 4))
         (>! in-ch [{:aa x}])
         (when (< x 31) 
           (recur (inc x)))))

    (is (= (count @(spawn-fiber get-reset ev-queue)) 
           32) 
        "rcv all 32 msgs")

    (let [prc-cnt (<!! (get-process-cnt cmd-ch))] 
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



