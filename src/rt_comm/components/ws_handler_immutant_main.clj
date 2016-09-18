(ns rt-comm.components.ws-handler-immutant-main
  (:require [rt-comm.connect-auth :refer [connect-process auth-process]] 
            [rt-comm.utils.utils :refer [valp]]

            [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]
            [clojure.core.match :refer [match]]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [taoensso.timbre :refer [debug info error spy]]
            ))

;;
;; (defn init-ws-user! [user-id user-socket ws-conns ev-queue]
;;   (let [incoming-socket-source (s/->source user-socket) 
;;         outgoing-socket-sink   (s/->sink user-socket) 
;;         incoming-actor (spawn ,,,)
;;         outgoing-actor nil]
;;
;;     (swap! ws-conns conj {:user-id        user-id
;;                           :socket         user-socket
;;                           :incoming-actor incoming-actor
;;                           :outgoing-actor nil})))
;;


;; -------------------------------------------------------------------------------

(defn make-handler [ws-conns event-queue]
  (fn [request]  ;; client requests a ws connection here

    (let [ch-incoming (channel 16 :displace true true) ;; Receives incoming user msgs. Will never block. Should not overflow/drop messages as upstream consumer batches messages. 
          [on-open-user-socket on-close-msg on-error-err] (repeatedly 3 p/promise) 

          auth-ws-user-args {:ch-incoming          ch-incoming
                             :on-open-user-socket  on-open-user-socket}  
                             ;:user-id              nil ;; Will be provided in auth-process - auth-result
                             ;:user-socket-outgoing nil ;; Will be provide by @on-open-user-socket
                             
          init-ws-user-args {:on-close-msg  on-close-msg
                             :on-error-err  on-error-err
                             :ws-conns      ws-conns
                             :event-queue   event-queue}

          immut-cbs {:on-open    (fn [user-socket] (deliver on-open-user-socket user-socket)) 
                     :on-close   (fn [_ ex] (deliver on-close-msg ex))
                     :on-error   (fn [_ e]  (deliver on-error-err e))
                     :on-message (fn [_ msg] (snd ch-incoming msg))} ;; Feed all incoming msgs into buffered dropping channel - will never block 
          ]
      (fiber (some-> auth-ws-user-args 
                     (connect-process 200) ;; wait for connection
                     (auth-process async/send! async/close 200) ;; returns augmented init-ws-user-args or nil
                     (merge init-ws-user-args)
                     #_init-ws-user!))
      (async/as-channel request immut-cbs) ;; Does not block. Returns ring response. Could use user-socket in response :body 
      )))

;; TEST CODE: manual
;; (do
;; (def ch (channel))
;; (def on-open-user-socket (p/promise))
;; (def auth-ws-user-args {:ch-incoming          ch
;;                         :on-open-user-socket  on-open-user-socket
;;                         :server :immutant})  
;;
;; (def calls (atom []))
;; (def send! (fn [ch msg] (swap! calls conj msg)))
;; (def close (fn [ch] (swap! calls conj "closed!")))
;; (def user-socket (channel))
;;
;; (def fib-rt (future (some-> auth-ws-user-args 
;;                            (connect-process 4000) 
;;                            (auth-process send! close 4000))))
;; )
;;
;; (p/closed? user-socket)
;; (deliver on-open-user-socket user-socket)
;; (future (snd ch {:cmd [:auth {:user-id "pete" :pw "abc"}]}))
;; (deref fib-rt)

;; TEST CODE: combined
;; (let [ch (channel)
;;       on-open-user-socket (p/promise)
;;       auth-ws-user-args {:ch-incoming          ch
;;                          :on-open-user-socket  on-open-user-socket
;;                          :server :immutant}  
;;
;;       _ (def calls (atom []))
;;       send! (fn [ch msg] (swap! calls conj msg))
;;       close (fn [ch] (swap! calls conj "closed!"))
;;       user-socket (channel)
;;
;;       _ (def fib-rt (fiber (some-> auth-ws-user-args 
;;                                    (connect-process 200) 
;;                                    (auth-process send! close 200))))
;;       ]
;;   (sleep 190)
;;   (deliver on-open-user-socket user-socket)
;;   (sleep 190)
;;   (future (snd ch {:cmd [:auth {:user-id "pete" :pw "abc"}]}))
;;   )
;;
;; (deref calls)
;; ["Login success!"]
;;
;; (deref fib-rt)
;; {:ch-incoming #object[co.paralleluniverse.strands.channels.TransferChannel 0xd2ae1a4 "co.paralleluniverse.strands.channels.TransferChannel@d2ae1a4"], 
;;  :on-open-user-socket #object[co.paralleluniverse.pulsar.core$promise$reify__20356 0x7fce6b49 {:status :ready, 
;;                                                                                                :val #object[co.paralleluniverse.strands.channels.TransferChannel 0x8b2dd64 "co.paralleluniverse.strands.channels.TransferChannel@8b2dd64"]}], 
;;  :server :immutant, 
;;  :user-socket #object[co.paralleluniverse.strands.channels.TransferChannel 0x8b2dd64 "co.paralleluniverse.strands.channels.TransferChannel@8b2dd64"], 
;;  :auth-result [:success "Login success!" "pete"], 
;;  :auth-success true, 
;;  :user-msg "Login success!", 
;;  :user-id "pete"}



(defrecord Ws-Handler-Immutant-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler [] #_(make-handler ws-conns event-queue)))

  (stop [component] component))



