(ns rt-comm.init-ws-user-test
  (:require [clojure.core.match :refer [match]]

            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!! alt! pipe
                                              close! put! take! thread timeout
                                              offer! poll! promise-chan
                                              sliding-buffer]]

            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox mailbox-of whereis 
                                                       register! unregister! self]]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]

            [manifold.deferred :as d]
            [manifold.stream :as s]

            [clojure.test :as t :refer [is are run-all-tests run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.components.event-queue :as event-queue]
            [rt-comm.utils.utils :refer [valp]]

            [rt-comm.components.event-queue :as eq] ;; testing only!

            [rt-comm.init-ws-user :refer :all]

            ))

(deftest connect-auth-init!-immut-test
  (let [!calls (atom [])
        ev-queue (spawn eq/server-actor [] 10)
        args {:server-snd-fn   (fn [ch msg] (swap! !calls conj msg))
              :server-close-fn (fn [ch] (swap! !calls conj "closed!"))
              :ws-conns     (atom [])
              :event-queue  ev-queue 
              :allowed-actns [:aa :bb]
              :batch-sample-intv 0
              }

        ch-incoming (a/chan)
        on-open-user-socket (promise)
        user-socket (a/chan)

        fb (fiber (connect-auth-init! (merge args {:ch-incoming          ch-incoming
                                                   :on-open-user-socket  on-open-user-socket
                                                   :server :immutant}) 
                                      30))]

    (sleep 10)
    (deliver on-open-user-socket user-socket) ;; connect event
    (sleep 10)
    (a/>!! ch-incoming {:actn :auth :data {:user-id "pete" :pw "abc"}}) ;; immutant

    (sleep 100)

    ;; (is (= (:auth-result (join fb))
    ;;        [:success "Login success!" "pete"]))
    (is (= (deref !calls) 
           ["Login success!"]))

    (sleep 10)

    (a/>!! ch-incoming [{:actn :bb :other 12} 
                        {:actn :aa :other 13} 
                        {:idx 14}])
    (a/>!! ch-incoming [{:actn :ada :other 16} 
                        {:other 15}])
    (a/>!! ch-incoming [{:actn :bb}])

    (sleep 100)

    (is (= @(spawn-fiber eq/get-reset ev-queue)
           [{:actn :bb, :other 12, :user-id "pete", :idx 0} 
            {:actn :aa, :other 13, :user-id "pete", :idx 1} 
            {:actn :bb, :user-id "pete", :idx 2}]) 
        "Ev-queue received filtered and augmented events.")

    ))


(deftest connect-auth-init!-aleph-test
  (let [!calls (atom [])
        ev-queue (spawn eq/server-actor [] 10)
        args {:server-snd-fn   (fn [ch msg] (swap! !calls conj msg))
               :server-close-fn (fn [ch] (swap! !calls conj "closed!"))
               :ws-conns     (atom [])
               :event-queue  ev-queue
               :allowed-actns [:aa :bb]
               :batch-sample-intv 0
               }

        on-open-user-socket (d/deferred)
        user-socket (s/stream)

        fb1 (fiber (connect-auth-init! (merge args {:on-open-user-socket  on-open-user-socket
                                                     :server :aleph}) 
                                       30))]

    (sleep 10)
    (deliver on-open-user-socket user-socket) ;; connect event
    (sleep 10)
    (s/put! user-socket {:actn :auth :data {:user-id "pete" :pw "abc"}})

    (sleep 100)

    (is (= (deref !calls) 
           ["Login success!"]))

    (sleep 10)

    (s/put! user-socket [{:actn :bb :other 12} 
                          {:actn :aa :other 13} 
                          {:idx 14}])
    (s/put! user-socket [{:actn :ada :other 16} 
                          {:other 15}])
    (s/put! user-socket [{:actn :bb}])

    (sleep 100)

    (is (= @(spawn-fiber eq/get-reset ev-queue)
           [{:actn :bb, :other 12, :user-id "pete", :idx 0} 
            {:actn :aa, :other 13, :user-id "pete", :idx 1} 
            {:actn :bb, :user-id "pete", :idx 2}]) 
        "Ev-queue received filtered and augmented events.")

    ))

;; (run-tests)


