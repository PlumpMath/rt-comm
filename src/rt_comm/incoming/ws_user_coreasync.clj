(ns rt-comm.incoming.ws-user-coreasync
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
            [rt-comm.utils.async :refer [rcv-rest pause-filter-keys]]

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




(defn process-msgs [msgs recip-chs]
  "Augment and filter msgs."
  (-> msgs 
      (add-to-col-in-table :recip-chans recip-chs)))

(defn commit! [msgs snd-ev-queue]
  "Commit msgs to event-queue."
  (snd-ev-queue [:append! msgs]))



(defn incoming-ws-user-actor [evt-ch snd-ev-queue {:keys [batch-sample-intv]}] 
  "Starts process that consumes msgs from evt-ch, applies state based transforms 
  and :append!s msgs to event-queue.
  Features: 
  - augment msgs based on settable state
  - :pause-rcv-overflow and :resume-rcv
  - batch/throttle incoming msgs using batch-sample-intv
  Gate into the system: Only concerned with msg-format and performance ops 
  that require state." 
  (let [ctr-ch (a/chan)] 

    (go-loop [recip-chs nil
              prc-cnt 0]

             (<! (timeout batch-sample-intv)) ;; wait for msgs to buffer in evt-ch

             (alt!   
               ;; CONTROL:
               ;; - receive state for processing
               ;; - pause/resume rcving msgs and let the windowed upstream ch overflow
               ;; - shutdown
               ctr-ch ([cmd] (match cmd
                                    [:set-fixed-recip-chs rcv-chs] (recur rcv-chs prc-cnt)  ;; TODO: expect a #{set}

                                    :pause-rcv-overflow (do (<! (pause-filter-keys ctr-ch :resume-rcv))
                                                            (recur recip-chs prc-cnt)) 

                                    [:shut-down msn] (info "Shut down incoming-ws-user-actor." msn)
                                    [:debug-prc-cnt prm] (do (deliver prm prc-cnt)
                                                             (recur recip-chs 0))
                                    :else (recur recip-chs prc-cnt)))
               ;; STREAM PROCESSING:
               ;; - receiving
               ;; - state-based filtering, augmenting
               ;; - forwarding
               evt-ch  ([first-new-msg] (do (-> (rcv-rest first-new-msg evt-ch)
                                                (process-msgs recip-chs)
                                                (commit! snd-ev-queue))
                                            (recur recip-chs (inc prc-cnt))))

               :priority true)) ;; handle ctrl-cmds with priority 
    ctr-ch)) 


;; TEST CODE:
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (! ev-queue [:append! [{:aa 23}]])
;; (def c1 (chan (sliding-buffer 4)))
;; (def c1 (chan))
;; (def ctr-ch (chan))
;; (def ie (incoming-ws-user-actor 
;;                c1 #(! ev-queue %) ctr-ch 
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
;; (>!! ctr-ch [:set-fixed-recip-chs #{:ach :bch}])
;; (>!! ctr-ch [:set-fixed-recip-chs nil])
;; (>!! ctr-ch :pause-rcv-overflow)
;; (>!! ctr-ch :resume-rcv)
;;
;; (def res (promise))
;; (>!! ctr-ch [:debug-prc-cnt res])
;; (deref res)
;;
;; (info "------")

