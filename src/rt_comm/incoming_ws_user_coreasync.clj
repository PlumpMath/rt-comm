(ns rt-comm.incoming-ws-user-coreasync
  (:require [rt-comm.utils.utils :as utils :refer [valp fpred recent-items]]

            [clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                              <!! >!!
                                              close! put! take! thread timeout
                                              offer! poll! promise-chan
                                              sliding-buffer]]
            [taoensso.timbre :refer [debug info error spy]])) 
;;
;; (defn batch-rcv-ev-colls [ch]
;;   "Poll! available event collections from ch and batch
;;   them into one event collection. Returns nil if no events were available. Non-blocking."
;;   (go-loop [v []
;;             x (a/poll! ch)]
;;            (if-not x
;;              (valp v seq)  ;; return nil if empty
;;              (recur (into v x)
;;                     (a/poll! ch)))))
;;
;; ;; TEST CODE:
;; ;; (def c1 (a/chan 6))
;; ;; (<!! (batch-rcv-ev-colls c1))
;; ;; (>!! c1 [2 3 4])
;; ;; (>!! c1 [5 6 7])
;;
;;
;; (defsfn take-all-or-wait [socket]
;;   "Batch-receive all available event-colls and return instantly 
;;   or wait for next single event coll."
;;   (if-some [new-msgs (batch-rcv-ev-colls socket)]
;;     new-msgs
;;     (p/rcv incoming-socket-source)))
;;
;;
;; (defsfn process-incoming! [socket receiver-chs event-queue]
;;   "Consume, process and send msgs to event-queue."
;;   (-> (take-all-or-wait socket)
;;       (augment-receiver-chans receiver-chs)
;;       (->> (conj [:append!]) 
;;            (! event-queue))))
;;
;;
;; (def incoming-ws-user-actor 
;;   "Consumes msgs from incm-socket-source and :append!s them to event-queue.
;;   Features: 
;;   - batch incm msgs 
;;   - validate msg cmds 
;;   - :pause-rcv-overflow
;;   - augment msgs with receive-chan ids
;;   - :shutdown" 
;;   (sfn [incm-socket-source event-queue {:keys [batch-sample-intv] :as conf}]
;;
;;        (loop [receiver-chs []]
;;
;;          (process-incoming! incm-socket-source 
;;                             receiver-chs 
;;                             event-queue)
;;
;;          ;; receive control cmds in-between msg processing
;;          (receive
;;            [:set-fixed-receiver-chans rcv-chs] (recur rcv-chs) 
;;
;;            ;; Pause processing incoming msgs and let the windowed socket overflow
;;            :pause-rcv-overflow (receive
;;                                  :resume-rcv (recur receiver-chs))
;;
;;            :shutdown (info "Shut down incoming-ws-user-actor. user-id:" (:user-id conf))
;;
;;            :after batch-sample-intv (recur receiver-chs)))))
;;
;; ;; TEST CODE:
;; ;; (require '[dev :refer [system]])
;; ;; (def ev-queue (-> system :event-queue :events-server))
;; ;; (! ev-queue [:append! [{:aa 23}]])
;; ;; (def c1 (p/channel 6))
;; ;; (def ia (spawn incoming-ws-user-actor 
;; ;;                c1 ev-queue
;; ;;                {:batch-sample-intv 0
;; ;;                 :allowed-cmds [:aa :bb :post-msg :set-receiver-chans]
;; ;;                 :user-id "pete"}))
;; ;;
;; ;; (snd c1 [{:aa 23} {:bb 23}])
;; ;; (snd c1 [{:aa 11} {:bb 38}])
;; ;; (snd c1 [{:aa 7} {:bb 57}])
;; ;; (snd c1 [2 3 4])
;; ;; (snd c1 [5 6 7])
;; ;;
;; ;; (batch-rcv-ev-colls c1)
;; ;;
;; ;; (info "------")
;;
;; ;; MESSAGE FORMAT EXAMPLES
;; ;; {:index      8562         ; gen by queue
;; ;;  :timestamp  "2016-0.."   ; gen by source or handler
;; ;;  :parent     8743         ; added for derived events
;; ;;  :user-id    "pete"       ; added for ws-connection after auth        
;; ;;  :to-chans   [:a :c]      ; qualify recipients of messages, added and maintained by inc-actor, can be set/overwritten by client in the message
;; ;; -> better: receiver-chans
;; ;;  }
;; ;;
;; ;; :action  :auth
;; ;; :data    {:user-id "pete" :pw "abc"}
;; ;;
;; ;; :action  :add-to-chans
;; ;; -> add-receiver-chans
;;
;; ;; :action  :del-to-chans
;; ;; :action  :set-to-chans
;; ;; :data    {:chan-ids [:b]}
;; ;;
;; ;; :action  :add-from-chans
;; ;; :action  :del-from-chans
;; ;; :action  :set-from-chans
;; ;; :data    {:chan-ids [:b]}
;; ;;
;; ;; :action  :post-msg
;; ;; :data    {:text "hi everyone!"}
;; ;;
;; ;; :action  :loc-update
;; ;; :data    {:loc [23 43]}
;; ;;
;; ;; {:cmd [:auth {:user-id "pete" :pw "abc"}]} ;; maintain system command layer?
;; ;;
;; ;; [{:client :abc :message [:join-room :chat4]} 
;; ;;  {:client :cde :message [:post "This is my text"]}
;; ;;  {:time 836, :location [14 43]}
;; ;;  {:time 853, :location [18 44]}
;; ;;  {:time 861, :location [24 46]}]
;;
;;
;; ;; {;:on-open-user-socket << << stream:  >> >>, 
;; ;;  ;:server :aleph, 
;; ;;  :user-socket << stream:  >>, 
;; ;;  ;:auth-result [:success "Login success!" "pete"], 
;; ;;  ;:auth-success true, 
;; ;;  ;:user-msg "Login success!", 
;; ;;  :user-id "pete"}
;; ;;
;; ;; {:ws-conns    ws-conns
;; ;;  :event-queue event-queue}
;;
;;
;; (defn assoc-user-id [user-id]
;;   (fn [m] (assoc m :user-id user-id)))
;;
;; ;; TODO: 
;; ;; write filter-invalid, 
;; ;; how to get conf data here?
;; ;; Aleph vs. Immutant adaption
;; ;; how to close conn?
;; ;; state based processing vs static stream pre-processing
;; set up static pre processing
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
;;
