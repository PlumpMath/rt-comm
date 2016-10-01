(ns rt-comm.incoming-ws-user-pulsar
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv fiber sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

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


(defsfn take-all-or-wait [socket]
  "Batch-receive all available event-colls and return instantly 
  or wait for next single event coll."
  (if-some [new-msgs (batch-rcv-ev-colls socket)]
    new-msgs
    (p/rcv socket)))

;; TEST CODE:
;; (def c1 (p/channel 6))
;; (def fb (fiber (take-all-or-wait c1)))
;; (deref fb 1000 :timeout)
;; (snd c1 [2 3 4])
;; (snd c1 [5 6 7])
;; (deref fb 1000 :timeout)


(defsfn process-incoming! [in-ch recip-chs event-queue]
  "Consume, process and send msgs to event-queue."
  (-> (take-all-or-wait in-ch)
      (add-to-col-in-table :recip-chans recip-chs)
      (->> (conj [:append!])
           (! event-queue))))

;; (defsfn process-incoming! [in-ch recip-chs event-queue]
;;   "Consume, process and send msgs to event-queue."
;;   (-> (take-all-or-wait in-ch)
;;       (as-> vm  ;; Conditional threading
;;         (if recip-chs 
;;           (add-to-col-in-table vm :recip-chans recip-chs) vm)
;;         (! event-queue [:append! vm]))))

;; TEST CODE:
;; (def c1 (p/channel 6))
;; (snd c1 [{:aa 1 :recip-chans [:a :c]} {:aa 2}])
;; (def a (spawn #(receive)))
;; (def r (fiber (process-incoming! c1 #{:b :c} a)))
;; (join a)
;; (def a (spawn #(receive)))
;; (def r (fiber (process-incoming! c1 nil a)))
;; (snd c1 [{:aa 1} {:aa 2}])
;; (join a)


(def incoming-ws-user-actor
  "Consumes msgs from incm-socket-source and :append!s them to event-queue.
  Features: 
  - batch incm msgs 
  - validate msg cmds 
  - :pause-rcv-overflow
  - augment msgs with receive-chan ids
  - :shutdown" 
  (sfn [incm-socket-source event-queue {:keys [batch-sample-intv]}]

       (loop [recip-chs nil]

         ;; STREAM PROCESSING:
         ;; - receiving
         ;; - state-based filtering, augmenting
         ;; - forwarding
         (process-incoming! incm-socket-source 
                            recip-chs 
                            event-queue)

         ;; CONTROL:
         ;; - receive state for processing
         ;; - pause processing
         ;; - control buffer/sample rate

         ;; receive control cmds in-between msg processing
         (receive
           [:set-fixed-receiver-chans rcv-chs] (recur rcv-chs)  ;; TODO: expect a #{set}

           ;; Pause processing incoming msgs and let the windowed socket overflow
           :pause-rcv-overflow (receive
                                 :resume-rcv (recur recip-chs))

           [:shutdown msn] (info "Shut down incoming-ws-user-actor." msn)

           :after batch-sample-intv (recur recip-chs)))))

;; TEST CODE:
(require '[dev :refer [system]])
(def ev-queue (-> system :event-queue :events-server))
(! ev-queue [:append! [{:aa 23}]])
(def c1 (p/channel 6))
(def ie (spawn incoming-ws-user-actor 
               c1 ev-queue
               {:batch-sample-intv 0}))

(snd c1 [{:aa 23} {:bb 23}])
(snd c1 [{:aa 11} {:bb 38}])

(! ie [:set-fixed-receiver-chans #{:ach :bch}])
(! ie :pause-rcv-overflow)
(! ie :resume-rcv)

(info "------")

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


(defn assoc-user-id [user-id]
  (fn [m] (assoc m :user-id user-id)))

;; TODO: 
;; write filter-invalid, 
;; how to get conf data here?
;; Aleph vs. Immutant adaption
;; how to close conn?
;; state based processing vs static stream pre-processing
;; set up static pre processing


;; (defn incoming-stream-aleph [user-socket user-id allowed-cmds]
;;   (->> user-socket 
;;        (s/filter (filter-invalid allowed-cmds)) 
;;        (s/map    (assoc-user-id user-id))))
;;
;;
;; (def allowed-cmds [:aa :bb :post-msg :set-receiver-chans])


