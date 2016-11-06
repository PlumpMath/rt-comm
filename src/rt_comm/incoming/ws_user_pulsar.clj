(ns rt-comm.incoming.ws-user-pulsar
  (:require [rt-comm.utils.utils :as u :refer [valp]]
            [rt-comm.utils.async-pulsar :as up]
            [rt-comm.components.event-queue :as eq] ;; testing only!

            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd select sel join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.core.match :refer [match]]

            [taoensso.timbre :refer [debug info error spy]]))



(defn process-msgs 
  "Augment and filter msgs."
  [msgs recip-chs]
  (-> msgs 
      (u/add-to-col-in-table :tags recip-chs)))

(defn commit! 
  "Commit msgs to event-queue."
  [msgs snd-ev-queue]
  (snd-ev-queue [:append! msgs]))


(def incoming-ws-user-actor
  "Consumes msgs from evt-ch, applies state based transforms 
  and :append!s msgs to event-queue.
  Features: 
  - augment msgs based on settable state
  - :pause-rcv-overflow and :resume-rcv
  - batch/throttle incoming msgs using batch-sample-intv
  Gate into the system: Only concerned with msg-format and performance ops 
  that require state." 
  (sfn [evt-ch snd-ev-queue {:keys [batch-sample-intv]}]

       (loop [state {:maintained-tags nil ;; set of keys will be added to each events :tags coll 
                     :prc-cnt 0}] ;; for monitoring: the number of ev-colls processed
         (sleep batch-sample-intv) ;; wait for msgs to buffer in evt-ch

         (select :priority true ;; handle ctrl-cmds with priority 
                 ;; CONTROL:
                 ;; - receive state for processing
                 ;; - pause/resume rcving msgs and let the windowed upstream ch overflow
                 ;; - shutdown
                 @mailbox ([cmd] (match cmd
                                        [:maintained-tags tags] (recur (assoc state :maintained-tags tags))  ;; TODO: expect a #{set}
                                        :pause-rcv-overflow (receive  
                                                              :resume-rcv (recur state))
                                        [:shut-down msn] (info "Shut down incoming-ws-user-actor." msn)
                                        [:debug-prc-cnt client] (do (! client [:rcv (:prc-cnt state)])
                                                                    (recur (assoc state :prc-cnt 0)))
                                        :else (recur state)))
                 ;; STREAM PROCESSING:
                 ;; - receiving
                 ;; - state-based filtering, augmenting
                 ;; - forwarding
                 evt-ch   ([first-new-msg] (do (-> (up/rcv-rest first-new-msg evt-ch)
                                                   (process-msgs (:maintained-tags state))
                                                   (commit! snd-ev-queue))
                                               (recur (update state :prc-cnt inc))))))))


;; TEST CODE:
;; (require '[dev :refer [system]])
;; (def ev-queue (-> system :event-queue :events-server))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (! ev-queue [:append! [{:aa 23}]])
;; (def c1 (p/channel 2 :displace))
;; (def ie (spawn incoming-ws-user-actor 
;;                c1 #(! ev-queue %) 
;;                {:batch-sample-intv 0}))
;; ;; next: message format wrong?
;; (snd c1 [{:aa 23} {:bb 23}])
;; (snd c1 [{:aa 11 :tags #{:cc :dd}} {:bb 38}])
;;
;; (! ie [:maintained-tags #{:ach :bch}])
;; (! ie [:maintained-tags nil])
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





