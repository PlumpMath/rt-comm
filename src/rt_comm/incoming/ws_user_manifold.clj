(ns rt-comm.incoming.ws-user-manifold
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
            [rt-comm.utils.async-manifold :refer [rcv-rest pause-filter-keys]]

            [manifold.stream :as s]
            [manifold.deferred :as d]

            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox mailbox-of whereis 
                                                       register! unregister! self]]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]


            [clojure.core.match :refer [match]]

            [rt-comm.components.event-queue :as eq] ;; testing only!
            [taoensso.timbre :refer [debug info error spy]]))


(defn is-ev-coll? [v]
  (some-> v (get 0) map?))


(defn process-msgs [msgs recip-chs]
  "Augment and filter msgs."
  (-> msgs 
      (add-to-col-in-table :recip-chans recip-chs)))

(defn commit! [msgs snd-event-queue]
  "Commit msgs to event-queue."
  (snd-event-queue [:append! msgs]))



(defn incoming-ws-user-actor [evt-ch snd-ev-queue {:keys [batch-sample-intv]}] 
  "Starts process that consumes msgs from evt-ch, applies state based transforms 
  and :append!s msgs to event-queue.
  Features: 
  - augment msgs based on settable state
  - :pause-rcv-overflow and :resume-rcv (note: consumes and drops incoming evts during pause!)
  - batch/throttle incoming msgs using batch-sample-intv
  Gate into the system: Only concerned with msg-format and performance ops 
  that require state." 
  (let [ctr-ch (s/stream)] 
    (s/connect evt-ch ctr-ch {:description "evt-ch -> ctr-ch"}) 

    (d/loop [state {:recip-chs nil 
                    :prc-cnt   0}]
      (d/chain 
        (-> (d/deferred) (d/timeout! batch-sample-intv nil)) ;; wait for msgs to buffer in evt-ch 
        (fn [_] (s/take! ctr-ch)) ;; take evt-coll or ctr-cmd 
        (fn [evt-ctr] (if (is-ev-coll? evt-ctr)
                        ;; STREAM PROCESSING:
                        ;; - receiving
                        ;; - state-based filtering, augmenting
                        ;; - forwarding
                        (do (-> (rcv-rest evt-ctr evt-ch)
                                (process-msgs (:recip-chs state))
                                (commit! snd-ev-queue))
                            (update state :prc-cnt inc))
                        ;; CONTROL:
                        ;; - receive state for processing
                        ;; - set pause and shut-down flags
                        (match evt-ctr 
                               [:fixed-recip-chs rcv-chs] (assoc state :recip-chs rcv-chs)  ;; TODO: expect a #{set}
                               :pause-rcv-overflow        (assoc state :pause true) 
                               [:shut-down msg]           (assoc state :shut-down msg) 
                               [:debug-prc-cnt prm]  (do (deliver prm (:prc-cnt state))
                                                         (assoc state :prc-cnt 0))
                               :else state)))
        (fn [state-n]
          (d/let-flow [on-resume (when (:pause state-n)
                                   (pause-filter-keys ctr-ch :resume-rcv)) ;; return deferred 
                       shut-down (when (:shut-down state-n)
                                   (info "Shut down incoming-ws-user-actor." (:shut-down state-n))
                                   true)]
            (when-not shut-down  
              on-resume ;; wait for :resume-rcv
              (-> (select-keys state-n [:recip-chs :prc-cnt]) 
                  d/recur))))))
    ctr-ch))


;; TEST CODE:
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (! ev-queue [:append! [{:aa 23}]])
;; (def c1 (s/stream 4))
;; (def cmd-ch (s/stream))
;; (def ie (incoming-ws-user-actor 
;;                c1 #(! ev-queue %) cmd-ch 
;;                {:batch-sample-intv 0}))
;;
;; (dotimes [x 8] 
;;   (s/put! c1 [{:a x}]))
;;
;; (def res (d/loop [x 0] 
;;            (d/chain (-> (d/deferred) (d/timeout! 1000 nil))
;;                     (fn [_] (s/put! c1 [{:a x}]))
;;                     (fn [_] (when (< x 7) 
;;                               (d/recur (inc x)))))))
;;
;; (s/put! c1 [{:aa 23} {:bb 23}])
;; (s/put! c1 [{:aa 11 :recip-chans #{:cc :dd}} {:bb 38}])
;;
;; (s/put! cmd-ch [:fixed-recip-chs #{:ach :bch}])
;; (s/put! cmd-ch [:fixed-recip-chs nil])
;; (s/put! cmd-ch :pause-rcv-overflow)
;; (s/put! cmd-ch :resume-rcv)
;;
;; (def res (promise))
;; (s/put! cmd-ch [:debug-prc-cnt res])
;; (deref res)
;;
;; (s/put! cmd-ch [:shut-down "Buye!"])
;;
;; (info "------")
;;

