(ns rt-comm.incoming-ws-user-pulsar
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]]
            [rt-comm.components.event-queue :as eq] ;; testing only!

            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd select sel join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.core.match :refer [match]]

            [taoensso.timbre :refer [debug info error spy]]))


(defn batch-rcv-ev-colls [ch]
  "Rcv available event collections from ch and batch
  them into one event collection. Returns nil if no events were available. Non-blocking."
  (loop [v []
         x (p/try-rcv ch)]
    (if-not x
      (valp v seq)  ;; return nil if empty
      (recur (into v x)
             (p/try-rcv ch)))))

;; TEST CODE:
;; (def c1 (p/channel 6))
;; (batch-rcv-ev-colls c1)
;; (snd c1 [2 3 4])
;; (snd c1 [5 6 7])


(defn rcv-rest [first-msg in-ch]
  "Rcv available msgs and append to first-msg vec. Never blocks."
  (->> (batch-rcv-ev-colls in-ch) ;; rcv other msgs or nil
       (into first-msg))) ;; into one vec of maps 

(defn process-msgs [msgs recip-chs]
  "Augment and filter msgs."
  (-> msgs 
      (add-to-col-in-table :recip-chans recip-chs)))

(defn commit! [msgs event-queue]
  "Commit msgs to event-queue."
  (! event-queue [:append! msgs]))


(def incoming-ws-user-actor
  "Consumes msgs from in-ch, applies state based transforms 
  and :append!s msgs to event-queue.
  Features: 
  - augment msgs based on settable state
  - :pause-rcv-overflow and :resume-rcv
  - batch/throttle incoming msgs using batch-sample-intv
  Gate into the system: Only concerned with msg-format and performance ops 
  that require state." 
  (sfn [in-ch event-queue {:keys [batch-sample-intv]}]

       (loop [recip-chs nil
              prc-cnt 0]
         (sleep batch-sample-intv) ;; wait for msgs to buffer in in-ch

         (select :priority true ;; handle ctrl-cmds with priority 
                 ;; CONTROL:
                 ;; - receive state for processing
                 ;; - pause/resume rcving msgs and let the windowed upstream ch overflow
                 ;; - shutdown
                 @mailbox ([cmd] (match cmd
                                        [:set-fixed-recip-chs rcv-chs] (recur rcv-chs prc-cnt)  ;; TODO: expect a #{set}
                                        :pause-rcv-overflow (receive  
                                                              :resume-rcv (recur recip-chs prc-cnt))
                                        [:shut-down msn] (info "Shut 2down incoming-ws-user-actor." msn)
                                        [:debug-prc-cnt client] (do (! client [:rcv prc-cnt])
                                                                    (recur recip-chs 0))
                                        :else (recur recip-chs prc-cnt)))
                 ;; STREAM PROCESSING:
                 ;; - receiving
                 ;; - state-based filtering, augmenting
                 ;; - forwarding
                 in-ch    ([first-new-msg] (do (-> (rcv-rest first-new-msg in-ch)
                                                   (process-msgs recip-chs)
                                                   (commit! event-queue))
                                               (recur recip-chs (inc prc-cnt))))))))


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

;; MESSAGE FORMAT EXAMPLES
;; {:index      8562         ; gen by queue
;;  :timestamp  "2016-0.."   ; gen by source or handler
;;  :parent     8743         ; added for derived events
;;  :user-id    "pete"       ; added for ws-connection after auth        
;;  :recip-chans [:a :c]      ; qualify recipients of messages, added and maintained by inc-actor, can be set/overwritten by client in the message
;;  }
;;
;; :action  :auth
;; :data    {:user-id "pete" :pw "abc"}
;;
;; :action  :add-to-chans
;; -> add-receiver-chans
;;
;; :action  :del-to-chans
;; :action  :set-to-chans
;; :data    {:chan-ids [:b]}
;;
;; :action  :add-from-chans
;; :action  :del-from-chans
;; :action  :set-from-chans
;; :data    {:chan-ids [:b]}
;;
;; :action  :post-msg
;; :data    {:text "hi everyone!"}
;;
;; :action  :loc-update
;; :data    {:loc [23 43]}
;;
;; {:cmd [:auth {:user-id "pete" :pw "abc"}]} ;; maintain system command layer?
;;
;; [{:client :abc :message [:join-room :chat4]} 
;;  {:client :cde :message [:post "This is my text"]}
;;  {:time 836, :location [14 43]}
;;  {:time 853, :location [18 44]}
;;  {:time 861, :location [24 46]}]
;;
;;
;; {;:on-open-user-socket << << stream:  >> >>, 
;;  ;:server :aleph, 
;;  :user-socket << stream:  >>, 
;;  ;:auth-result [:success "Login success!" "pete"], 
;;  ;:auth-success true, 
;;  ;:user-msg "Login success!", 
;;  :user-id "pete"}
;;
;; {:ws-conns    ws-conns
;;  :event-queue event-queue}


;; (defn assoc-user-id [user-id]
;;   (fn [m] (assoc m :user-id user-id)))
;;
;; ;; TODO: 
;; ;; write filter-invalid, 
;; ;; how to get conf data here?
;; ;; Aleph vs. Immutant adaption
;; ;; how to close conn?
;; ;; state based processing vs static stream pre-processing
;; ;; set up static pre processing
;;
;;
;; (defn incoming-stream-aleph [user-socket user-id allowed-cmds]
;;   (->> user-socket 
;;        (s/filter (filter-invalid allowed-cmds)) 
;;        (s/map    (assoc-user-id user-id))))
;;
;;
;; (def allowed-cmds [:aa :bb :post-msg :set-receiver-chans])
;;
;; (def s1 (s/stream 6))



