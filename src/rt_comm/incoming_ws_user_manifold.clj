(ns rt-comm.incoming-ws-user-manifold
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
            [rt-comm.utils.async :refer [rcv-rest pause-filter-keys]]

            [manifold.stream :as s]
            [manifold.deferred :as d]

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


(defn process-msgs [msgs recip-chs]
  "Augment and filter msgs."
  (-> msgs 
      (add-to-col-in-table :recip-chans recip-chs)))

(defn commit! [msgs snd-event-queue]
  "Commit msgs to event-queue."
  (snd-event-queue [:append! msgs]))

;;
;;
;; (apply d/recur state)
;;
;; (defn incoming-ws-user-actor [in-ch snd-event-queue cmd-ch {:keys [batch-sample-intv]}] 
;;   "Consumes msgs from in-ch, applies state based transforms 
;;   and :append!s msgs to event-queue.
;;   Features: 
;;   - augment msgs based on settable state
;;   - :pause-rcv-overflow and :resume-rcv
;;   - batch/throttle incoming msgs using batch-sample-intv
;;   Gate into the system: Only concerned with msg-format and performance ops 
;;   that require state." 
;;   (d/loop [st {:recip-chs nil 
;;                :prc-cnt   0}]
;;
;;     (d/chain 
;;       (t/in batch-sample-intv (constantly nil))  ;; wait for msgs to buffer in in-ch 
;;
;;       (s/try-take! cmd-ch 0) 
;;
;;       ;; pause?
;;       (fn [cmd] (if (= cmd :pause-rcv-overflow)
;;                   (pause-filter-keys cmd-ch :resume-rcv)
;;                   cmd))
;;       ;; ;; pause = could just not release that var state
;;       ;; :pause-rcv-overflow (do (<! (pause-filter-keys cmd-ch :resume-rcv))
;;       ;;                         [recip-chs prc-cnt]) 
;;
;;
;;       ;; Include state in threading
;;       (fn [cmd] {:cmd cmd
;;                  :state st})
;;
;;       ;; Translate cmd into new state
;;       cmd->state
;;
;;       (defn cmd->state [args] 
;;         (if-not (:cmd args) args 
;;           (match (:cmd args) 
;;
;;                  [:set-fixed-recip-chs rcv-chs] 
;;                  (assoc-in args [:state :recip-chs] rcv-chs)  ;; TODO: expect a #{set}
;;
;;                  [:shut-down msn] 
;;                  (assoc args :shut-down true :shut-down-msg msg)
;;
;;                  [:debug-prc-cnt prm] 
;;                  (do (deliver prm prc-cnt)
;;                      (assoc-in args [:state :prc-cnt] 0))
;;
;;                  :else args)))
;;
;;
;;       (fn [m] 
;;         (assoc m :msg @(s/take! stream))) 
;;
;;
;;
;;       ;; wait for and append the new message
;;       (fn [args] 
;;         (if-not (:shut-down args) 
;;           (assoc args :msg @(s/take! in-ch))
;;           args)) 
;;
;;
;;       ;; get most recent cmd
;;       (fn [args] 
;;         (assoc args :cmd @(s/try-take! cmd-ch 0)))
;;
;;       cmd->state
;;
;;       ;; handle msg!
;;       (fn [[state msg]] 
;;         (when state (do (-> (rcv-rest msg in-ch)
;;                             (process-msgs (:recip-chs state))
;;                             (commit! snd-event-queue))
;;                         (update state :prc-cnt inc))))
;;
;;       (d/let-flow [msg (if state (s/take! in-ch))
;;
;;                    ;; needs to:
;;                    ;; - wait for state
;;                    ;; - skip at nil state - not recur
;;                    ;; - use updated state variable
;;
;;                    state (if-not msg [recip-chs prc-cnt]
;;                            (do (-> (rcv-rest msg in-ch)
;;                                    (process-msgs recip-chs)
;;                                    (commit! snd-event-queue))
;;                                (update state 1 inc)) 
;;
;;                            )
;;
;;                    ])
;;
;;       (d/let-flow [cmd   (s/try-take! cmd-ch 0)
;;                    state (if-not cmd 
;;                            [recip-chs prc-cnt] 
;;                            (match cmd ;; state change can be used in the same run
;;                                   [:set-fixed-recip-chs rcv-chs] [rcv-chs prc-cnt]  ;; TODO: expect a #{set}
;;                                   ;; pause = could just not release that var state
;;                                   :pause-rcv-overflow (do (<! (pause-filter-keys cmd-ch :resume-rcv))
;;                                                           [recip-chs prc-cnt]) 
;;                                   ;; no state - not continue
;;                                   [:shut-down msn] (info "Shut down incoming-ws-user-actor." msn)
;;                                   ;; do right away
;;                                   [:debug-prc-cnt prm] (do (deliver prm prc-cnt)
;;                                                            [recip-chs 0])
;;                                   :else [recip-chs prc-cnt]))  
;;
;;
;;                    ]
;;         (d/let-flow [
;;                      ;; msg   (s/try-take! in-ch 200)
;;                      msg   (if state (s/take! in-ch))
;;
;;                      ;; needs to:
;;                      ;; - wait for state
;;                      ;; - skip at nil state - not recur
;;                      ;; - use updated state variable
;;
;;                      state (if-not msg [recip-chs prc-cnt]
;;                              (do (-> (rcv-rest msg in-ch)
;;                                      (process-msgs recip-chs)
;;                                      (commit! snd-event-queue))
;;                                  (update state 1 inc)) 
;;
;;                              )
;;
;;                      ]))
;;
;;
;;
;;
;; )
;;
;;
;; (alt!   
;;   ;; CONTROL:
;;   ;; - receive state for processing
;;   ;; - pause/resume rcving msgs and let the windowed upstream ch overflow
;;   ;; - shutdown
;;   cmd-ch ([cmd] (match cmd
;;                        [:set-fixed-recip-chs rcv-chs] (recur rcv-chs prc-cnt)  ;; TODO: expect a #{set}
;;
;;                        :pause-rcv-overflow (do (<! (pause-filter-keys cmd-ch :resume-rcv))
;;                                                (recur recip-chs prc-cnt)) 
;;
;;                        [:shut-down msn] (info "Shut down incoming-ws-user-actor." msn)
;;                        [:debug-prc-cnt prm] (do (deliver prm prc-cnt)
;;                                                 (recur recip-chs 0))
;;                        :else (recur recip-chs prc-cnt)))
;;   ;; STREAM PROCESSING:
;;   ;; - receiving
;;   ;; - state-based filtering, augmenting
;;   ;; - forwarding
;;   in-ch  ([first-new-msg] (do (-> (rcv-rest first-new-msg in-ch)
;;                                   (process-msgs recip-chs)
;;                                   (commit! snd-event-queue))
;;                               (recur recip-chs (inc prc-cnt))))
;;
;;   :priority true))) ;; handle ctrl-cmds with priority


;; TEST CODE:
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (! ev-queue [:append! [{:aa 23}]])
;; (def c1 (chan (sliding-buffer 4)))
;; (def c1 (chan))
;; (def cmd-ch (chan))
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

