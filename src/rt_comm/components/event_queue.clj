(ns rt-comm.components.event-queue
  (:refer-clojure :exclude [promise await])
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :refer [debug info error spy] :rename {debug <<<}]

    [rt-comm.utils.utils :as utils :refer [valp fpred recent-items]]

    [co.paralleluniverse.pulsar.core :refer [sfn defsfn snd join spawn-fiber sleep]]
    [co.paralleluniverse.pulsar.async :as pa]
    [co.paralleluniverse.pulsar.actors :refer [receive !! ! spawn mailbox-of whereis 
                                               register! unregister! self]]
    ))


;; -------------------------------------------------------------------------------
;; Helpers

(defn into-buffer [buffer max-size new-items]
  "Appends new-items (vec) to buffer (vec), 
  then crops buffer to max-size."
  (let [buffer-new-items (apply conj buffer new-items)
        to-trim-count    (- (count buffer-new-items) max-size)] ;; how much more items than max-count?
    (if (pos? to-trim-count) 
      (subvec buffer-new-items to-trim-count)
      buffer-new-items)))
;; (into-buffer [1 2 3] 4 [5 6 7])

(defn latest-index [events] 
  "Latest :idx val in event-queue data, or -1 if not found."
  (-> (-> events last :idx)
      (valp integer? -1)))

(defn add-index [events start-idx]
  "Add an :idx column to a vector of maps."
  (into [] (map-indexed 
             (fn [idx el]
               (assoc el :idx (+ idx start-idx)))
             events)))

;; -------------------------------------------------------------------------------

(defn load-events [file-path]
  [{:idx 3 :time 336 :location [12 33]} 
   {:idx 4 :time 350 :location [16 43]}
   {:idx 5 :time 353 :location [18 40]}
   {:idx 6 :time 361 :location [21 36]}
   ])

(defn save-events! [events path]
  ;; TODO
  )



;; this will become the websocket client
(def ca 
  (sfn client-actor [ev-server]
       (loop [cur-idx 0]
         (receive
           [:deliver-new events] (do (<<< "client:" (.getName @self) ":deliver-new" events)
                                     (recur (spy (latest-index events))))

           :next (do (! ev-server [:req-from-idx @self cur-idx])
                     (recur cur-idx))

           :shutdown! (<<< "shutting down client actor: " (.getName @self))

           :else (do (<<< "Unsupported message to client!")
                     (recur cur-idx))
           ))))


;; -------------------------------------------------------------------------------
;; Debugging

(def !debug-state (atom :nothing))

(defn debug-log-state [queue-data pend-reqs]
  (reset! !debug-state {:cur-idx   (latest-index queue-data)
                        :pend-reqs pend-reqs
                        :queue     queue-data
                        })
  (<<< "Event-queue queue data: " queue-data) 
  (<<< "Event-queue pending requests: " pend-reqs))

(defn debug-state! [ev-server]
  "Returns the current state of the events-server actor."
  (join (spawn-fiber 
          (fn [] (let [server-actor ev-server]
                   (!! server-actor :debug-state)
                   (sleep 10) ;; let the actor update the atom first. TODO: how to do this better?
                   @!debug-state ;; this fiber -and main thread?- will sleep, while actor fiber thread will update the atom
                   )))))

(defn <>! [id mes]
  "Send message if actor found."
  (some-> (whereis id 20 :ms) (! mes)))


(defn start-client-debug [a-name]
  "Start test-client to event-server"
  (some-> (whereis a-name 10 :ms)  ;; cleanup
          (doto unregister! (!! :shutdown!)))

  (if-let [ev-srv (whereis :events-server 20 :ms)]
    (register! a-name (spawn ca ev-srv))
    (<<< "ev-server not found!")))


;; -------------------------------------------------------------------------------




#_(start-client-debug :c4)

;; Client is done sending messages to browser and ready to request messages/samples that have meanwhile arrived in the queue/event-server.
;; (<>! :c3 :next)

;; Server will then reply at one point and send a vec of events
#_(<>! :c3 [:deliver-new [{:idx 14} {:idx 15}]])

;; Debug server state
(some-> (whereis :events-server 20 :ms) 
        debug-state! 
        #_:cur-idx #_:pend-reqs :queue
        )
;; ==>
[{:idx  5, :time 353, :location [18 40]}
 {:idx  6, :time 361, :location [21 36]}
 {:client :abc, :message [:join-room :chat4], :idx 7}
 {:client :cde, :message [:post "This is my text"], :idx 8}
 {:time   836, :location [14 43], :idx 9}
 {:time   853, :location [18 44], :idx 10}
 {:time   861, :location [24 46], :idx 11}]

;; Mimic a producer - append new messages
(<>! :events-server [:append! [{:client :abc :message [:join-room :chat4]} 
                               {:client :cde :message [:post "This is my text"]}
                               {:time 836, :location [14 43]}
                               {:time 853, :location [18 44]}
                               {:time 861, :location [24 46]}]])


;; A message is simply a map. No keys are required at this point. 
;; The :idx key will be generated automatically. The only purpose of :idx 
;; is to maintain the overall number of events after restarts and for debugging (should see continious index-numbers)

(def server-actor 
  "Collects events (things that have happened in the system) in a buffer and provides
  them to clients via request-reply.
  The server keeps clients as state only for the duration of their request-reply.
  Clients must implement the :deliver-new variant/route."
  (sfn event-server-actor [init-events max-size]
       (loop [pend-reqs [] ;; pending clients 
              queue     (valp init-events vector? [])]

         (receive
           ;; broadcast to pending clients and append to queue
           [:append! new-events] (do (<<< "New events:" new-events)  
                                     (let [evts-idx (->> (latest-index queue) inc 
                                                         (add-index new-events))] 
                                       ;; send right to pending clients and ..
                                       (doseq [client pend-reqs]
                                         (! client [:deliver-new evts-idx]))

                                       ;; append to queue
                                       (->> (into-buffer queue max-size evts-idx)
                                            (recur []))))

           ;; reply with new events immediately or conj to pending 
           [:req-from-idx client req-idx] (do (<<< ":req-from-idx " (.getName client) req-idx) 
                                              (if-let [nov-evts (-> (latest-index queue) 
                                                                    (- req-idx) ;; cnt of new items
                                                                    (recent-items queue))] ;; get new items or nil
                                                (do (! client [:deliver-new nov-evts]) ;; reply immediately
                                                    (<<< :nov-evts nov-evts)
                                                    (recur pend-reqs queue))

                                                (-> (spy (conj pend-reqs client)) ;; .. or conj to pend-reqs 
                                                    (recur queue))))

           :debug-state (do (debug-log-state queue pend-reqs)
                            (recur pend-reqs queue))

           [:deliver-state! prm] (do (deliver prm queue)
                                     (recur pend-reqs queue))

           [:get-queue client] (do (! client [:rcv queue])
                                   (recur pend-reqs queue))

           [:get-q-reset client] (do (! client [:rcv queue])
                                   (recur [] []))

           [:save-data file-path] (do (save-events! queue file-path) 
                                      (recur pend-reqs queue))

           :reset! (recur [] []) 
           :shutdown! (do (<<< "Shutting down event-queue-server ..")
                          ;; send message to pending request? to all clients? which order to shut down actors?
                          (debug-log-state queue pend-reqs)) 
           ))))


;; -------------------------------------------------------------------------------

(defrecord EventQueue [conf events-server]
  component/Lifecycle

  (start [component]
    (let [event-data    (load-events (:file-path conf))
          server-actor' (spawn server-actor event-data (:max-size conf))
          _             (register! :events-server server-actor')
          _             (start-client-debug :c3)
          ]  ;; registering is only for debugging!
      (assoc component :events-server server-actor')))

  (stop [component]
    (unregister! events-server)
    (! events-server :shutdown!)
    (assoc component :events-server nil))

  )

;; -------------------------------------------------------------------------------

#_(defn append! [events]
  (! ev-server [:append! events]))



