(ns rt-comm.incoming-ws-user-manifold
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
            ;; [rt-comm.utils.async-manifold :refer [rcv-rest pause-filter-keys]]

            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.time :as t]

            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!! alt! pipe
                                              close! put! take! thread timeout
                                              offer! poll! promise-chan
                                              sliding-buffer]]
            [clojure.core.match :refer [match]]

            [rt-comm.components.event-queue :as eq] ;; testing only!
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox mailbox-of whereis 
                                                       register! unregister! self]]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]

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


;;
;; (defn incoming-ws-user-actor [evt-ch snd-ev-queue ctr-ch {:keys [batch-sample-intv]}] 
;;   (s/connect evt-ch ctr-ch {:description "evt-ch -> ctr-ch"}) 
;;
;;   (d/loop [state {:recip-chs nil 
;;                   :prc-cnt   0}]
;;     (d/chain 
;;       (-> (d/deferred) (d/timeout! batch-sample-intv nil)) ;; wait for msgs to buffer in evt-ch 
;;       (fn [_] (s/take! ctr-ch)) ;; take evt-coll or ctr-cmd 
;;       (fn [evt-ctr] (if (is-ev-coll? evt-ctr)
;;                       ;; STREAM PROCESSING:
;;                       ;; - receiving
;;                       ;; - state-based filtering, augmenting
;;                       ;; - forwarding
;;                       (do (-> (rcv-rest evt-ctr evt-ch)
;;                               (process-msgs (:recip-chs state))
;;                               (commit! snd-ev-queue))
;;                           (update state :prc-cnt inc))
;;                       ;; CONTROL:
;;                       ;; - receive state for processing
;;                       ;; - set pause and shut-down flags
;;                       (match evt-ctr 
;;                              [:set-fixed-recip-chs rcv-chs] (assoc state :recip-chs rcv-chs)  ;; TODO: expect a #{set}
;;                              [:debug-prc-cnt prm]  (do (deliver prm prc-cnt)
;;                                                        (assoc state :prc-cnt 0))
;;                              :pause-rcv-overflow   (assoc state :pause true) 
;;                              [:shut-down msn]      (assoc state :shut-down msg) 
;;                              :else state)))
;;       (fn [state-n]
;;         (d/let-flow [on-resume (when (:pause state-n)
;;                                  (pause-filter-keys cmd-ch :resume-rcv)) ;; return deferred 
;;                      shut-down (when (:shut-down state-n)
;;                                  (info "Shut down incoming-ws-user-actor." (:shut-down state-n))
;;                                  true)]
;;           (when-not shut-down  
;;             on-resume ;; wait for :resume-rcv
;;             (d/recur state-n))))))) 


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
;;   (>!! c1 [{:a x}]))
;;
;; (go-loop [x 0] 
;;          (<! (timeout 0))
;;          (>! c1 [{:a x}])
;;          (when (< x 7) 
;;            (recur (inc x))))
;;
;; (>!! c1 [{:aa 23} {:bb 23}])
;; (>!! c1 [{:aa 11 :recip-chans #{:cc :dd}} {:bb 38}])
;;
;; (>!! cmd-ch [:set-fixed-recip-chs #{:ach :bch}])
;; (>!! cmd-ch [:set-fixed-recip-chs nil])
;; (>!! cmd-ch :pause-rcv-overflow)
;; (>!! cmd-ch :resume-rcv)
;;
;; (def res (promise))
;; (>!! cmd-ch [:debug-prc-cnt res])
;; (deref res)
;;
;; (info "------")

