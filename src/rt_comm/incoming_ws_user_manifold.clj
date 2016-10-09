(ns rt-comm.incoming-ws-user-manifold
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
            [rt-comm.utils.async :refer [rcv-rest pause-filter-keys]]

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


(defn cmd->state [cmd state] 
  "Match cmd and assoc into state."
  (if-not cmd state 
    (match cmd 
           [:set-fixed-recip-chs rcv-chs] 
           (assoc state :recip-chs rcv-chs)  ;; TODO: expect a #{set}

           [:debug-prc-cnt prm] 
           (do (deliver prm prc-cnt)
               (assoc state :prc-cnt 0))

           :else state)))


(defn incoming-ws-user-actor [in-ch snd-event-queue cmd-ch {:keys [batch-sample-intv]}] 
  (d/loop [state0 {:recip-chs nil 
                   :prc-cnt   0}]

    (d/chain 
      (t/in batch-sample-intv (constantly nil))  ;; wait for msgs to buffer in in-ch 

      (d/let-flow [cmd1       (s/try-take! cmd-ch 0) ;; check for cmd before msg take! ..
                   on-resume  (when (= cmd1 :pause-rcv-overflow)
                                (pause-filter-keys cmd-ch :resume-rcv)) ;; return deferred 
                   shut-down1 (when (= cmd1 :shut-down)  ;; TODO [:shut-down msg]
                                (info "Shut down incoming-ws-user-actor.")
                                true)]
        (when-not shut-down1  
          on-resume ;; wait for :resume-rcv
          (d/let-flow [msg    (s/take! in-ch)
                       cmd2   (do msg ;; wait until msg received!
                                  (s/try-take! cmd-ch 0)) ;; check for cmd after msg take! .. 

                       shut-down2 (when (= cmd2 :shut-down)  ;; TODO [:shut-down msg]
                                    (info "Shut down incoming-ws-user-actor.")
                                    true)

                       state1  (->> (cmd->state cmd1 state0) 
                                    (cmd->state cmd2))

                       state2  (when-not shut-down2 
                                 (do (-> (rcv-rest msg in-ch)
                                         (process-msgs (:recip-chs state1))
                                         (commit! snd-event-queue))
                                     (update state1 :prc-cnt inc)))]
            (when-not shut-down2 
              (d/recur state2)))))))) 



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

