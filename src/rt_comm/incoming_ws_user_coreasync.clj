(ns rt-comm.incoming-ws-user-coreasync
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 

            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!! alt! pipe
                                              close! put! take! thread timeout
                                              offer! poll! promise-chan
                                              sliding-buffer]]
            [clojure.core.match :refer [match]]

            [taoensso.timbre :refer [debug info error spy]])) 


(defn batch-rcv-ev-colls [ch]
  "Poll! available event collections from ch and batch
  them into one event collection. Returns nil if no events were available. Non-blocking."
  (go-loop [v []
            x (a/poll! ch)]
           (if-not x
             (valp v seq)  ;; return nil if empty
             (recur (into v x)
                    (a/poll! ch)))))

;; TEST CODE:
;; (def c1 (a/chan 6))
;; (<!! (batch-rcv-ev-colls c1))
;; (>!! c1 [2 3 4])
;; (>!! c1 [5 6 7])

(defn filter-msg-keys-xf [allowed-actns]
  "Returns a transducer that accepts only 
  variants/msgs of allowed-keys."
  (-> (comp (set allowed-actns) first)
      filter))


(defn rcv-msg-keys [in-ch & msg-keys]
  "Returns a chan that takes from in-ch and only
  transits variants with msg-keys."
  (->> (chan 2 (filter-msg-keys-xf msg-keys))
       (pipe in-ch)))


(defn rcv-msg-keys [in-ch & msg-keys]
  (->> (promise-chan (filter-msg-keys-xf msg-keys))
       (pipe in-ch)))

#_(defn rcv-msg-keys [in-ch & msg-keys]
  (let [pr-ch (promise-chan (filter-msg-keys-xf msg-keys))]
       (pipe in-ch pr-ch false)))



(defn rcv-rest [first-msg in-ch]
  "Rcv available msgs and append to first-msg vec. Never blocks."
  (->> (batch-rcv-ev-colls in-ch) ;; rcv other msgs or nil
       (into first-msg))) ;; into one vec of maps 

(defn process-msgs [msgs recip-chs]
  "Augment and filter msgs."
  (-> msgs 
      (add-to-col-in-table :recip-chans recip-chs)))

(defn commit! [msgs snd-event-queue]
  "Commit msgs to event-queue."
  (snd-event-queue [:append! msgs]))


(defn incoming-ws-user-actor [in-ch snd-event-queue cmd-ch {:keys [batch-sample-intv]}] 
  "Consumes msgs from in-ch, applies state based transforms 
  and :append!s msgs to event-queue.
  Features: 
  - augment msgs based on settable state
  - :pause-rcv-overflow and :resume-rcv
  - batch/throttle incoming msgs using batch-sample-intv
  Gate into the system: Only concerned with msg-format and performance ops 
  that require state." 
  (go-loop [recip-chs nil
            prc-cnt 0]

           (<! (timeout batch-sample-intv)) ;; wait for msgs to buffer in in-ch

           (alt!   
             ;; CONTROL:
             ;; - receive state for processing
             ;; - pause/resume rcving msgs and let the windowed upstream ch overflow
             ;; - shutdown
             cmd-ch ([cmd] (match cmd
                                  [:set-fixed-recip-chs rcv-chs] (recur rcv-chs prc-cnt)  ;; TODO: expect a #{set}

                                  ;; :pause-rcv-overflow (do (<! (rcv-msg-keys cmd-ch :resume-rcv))
                                  ;;                         (recur recip-chs prc-cnt)) 

                                  :pause-rcv-overflow (let [res-ch (rcv-msg-keys cmd-ch :resume-rcv)]
                                                        (<! res-ch)
                                                        (close! res-ch)
                                                        (recur recip-chs prc-cnt)) 

                                  [:shut-down msn] (info "Shut 2down incoming-ws-user-actor." msn)
                                  [:debug-prc-cnt prm] (do (deliver prm prc-cnt)
                                                           (recur recip-chs 0))
                                  :else (recur recip-chs prc-cnt)))
             ;; STREAM PROCESSING:
             ;; - receiving
             ;; - state-based filtering, augmenting
             ;; - forwarding
             in-ch  ([first-new-msg] (do (-> (rcv-rest first-new-msg in-ch)
                                             (process-msgs recip-chs)
                                             (commit! snd-event-queue))
                                         (recur recip-chs (inc prc-cnt))))

             :priority true))) ;; handle ctrl-cmds with priority? 



;; (defn aa [in1 in2]
;;   (go-loop [a 0]
;;            (alt!
;;              in1 ([x] (match x
;;                              0 (do (info a) 
;;                                    (recur (inc a)))
;;                              1 (do (info "1:" a) 
;;                                    (recur (inc a)))
;;                              :else "end"))
;;              in2 ([x] (do (info "in" a) 
;;                           (recur (inc a)))))))


;; (def in1 (chan))
;; (def in2 (chan))
;; (pipe in1 in2)
;;
;; ;; (aa in1 in2)
;;
;; (>!! in1 0)
;; (>!! in2 2)
;;
;;
;; (<!! in1)
;; (<!! in2)
;;
;; (info "---")

;; TEST CODE:
;; (def ch1 (chan))
;; (def ch2 (chan))
;; (pipe ch1 ch2)
;;
;; (def prch (rcv-msg-keys ch1 :resume-rcv))
;;
;; (future (let [res-ch (rcv-msg-keys ch1 :resume-rcv)]
;;           (info (<!! res-ch))
;;           (close! res-ch)))
;;
;; (>!! ch1 [:resume-rcv 40])
;;

;; TEST CODE:
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (! ev-queue [:append! [{:aa 23}]])
;; (def c1 (p/channel 2 :displace))
;; (def ie (spawn incoming-ws-user-actor 
;;                c1 ev-queue
;;                {:batch-sample-intv 0}))
;;
;; (snd c1 [{:aa 23} {:bb 23}])
;; (snd c1 [{:aa 11 :recip-chans #{:cc :dd}} {:bb 38}])
;;
;; (! ie [:set-fixed-recip-chs #{:ach :bch}])
;; (! ie [:set-fixed-recip-chs nil])
;; (! ie :pause-rcv-overflow)
;; (! ie :resume-rcv)
;;
;; (info "------")

