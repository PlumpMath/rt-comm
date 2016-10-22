(ns rt-comm.incoming.ws-user-coreasync
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
            [rt-comm.utils.async :as ua]

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
      (add-to-col-in-table :tags recip-chs)))

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

    (go-loop [state {:maintained-tags nil ;; set of keys will be added to each events :tags coll 
                    :prc-cnt 0}] ;; for monitoring: the number of ev-colls processed

             (<! (timeout batch-sample-intv)) ;; wait for msgs to buffer in evt-ch

             (alt!   
               ;; CONTROL:
               ;; - receive state for processing
               ;; - pause/resume rcving msgs and let the windowed upstream ch overflow
               ;; - shutdown
               ctr-ch ([cmd] (match cmd
                                    [:maintained-tags tags] (recur (assoc state :maintained-tags tags))  ;; TODO: expect a #{set}

                                    :pause-rcv-overflow (do (<! (ua/pause-filter-keys ctr-ch :resume-rcv))
                                                            (recur state)) 

                                    [:shut-down msn] (info "Shut down incoming-ws-user-actor." msn)
                                    [:debug-prc-cnt prm] (do (deliver prm (:prc-cnt state))
                                                             (recur (assoc state :prc-cnt 0)))
                                    :else (recur state)))
               ;; STREAM PROCESSING:
               ;; - receiving
               ;; - state-based filtering, augmenting
               ;; - forwarding
               evt-ch  ([first-new-msg] (do (-> (ua/rcv-rest first-new-msg evt-ch)
                                                (process-msgs (:maintained-tags state))
                                                (commit! snd-ev-queue))
                                            (recur (update state :prc-cnt inc))))

               :priority true)) ;; handle ctrl-cmds with priority 
    ctr-ch)) 


;; TEST CODE:
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (! ev-queue [:append! [{:aa 23}]])
;; (def c1 (chan (sliding-buffer 4)))
;; (def c1 (chan))
;; (def ctr-ch (incoming-ws-user-actor 
;;               c1 #(! ev-queue %)
;;               {:batch-sample-intv 9}))
;;
;; (dotimes [x 8] 
;;   (>!! c1 [{:a x}]))
;;
;; (go-loop [x 0] 
;;          (<! (timeout 3))
;;          (>! c1 [{:a x}])
;;          (when (< x 7) 
;;            (recur (inc x))))
;;
;; (>!! c1 [{:aa 23} {:bb 23}])
;; (>!! c1 [{:aa 11 :tags #{:cc :dd}} {:bb 38}])
;;
;; (>!! ctr-ch [:maintained-tags #{:ach :bch}])
;; (>!! ctr-ch [:maintained-tags nil])
;; (>!! ctr-ch :pause-rcv-overflow)
;; (>!! ctr-ch :resume-rcv)
;;
;; (def res (promise))
;; (>!! ctr-ch [:debug-prc-cnt res])
;; (deref res)
;;
;; (info "------")

