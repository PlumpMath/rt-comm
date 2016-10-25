(ns rt-comm.components.ws-handler-immutant-main
  (:require [rt-comm.incoming.connect-auth :refer [connect-process auth-process]] 
            [rt-comm.utils.utils :refer [valp]]
            [rt-comm.init-ws-user :as init-ws-user]

            [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]
            [clojure.core.match :refer [match]]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [taoensso.timbre :refer [debug info error spy]]
            ))

;; {:ch-incoming #object[co.paralleluniverse.strands.channels.TransferChannel 0x55217f49 "co.paralleluniverse.strands.channels.TransferChannel@55217f49"], 
;;  ;:on-open-user-socket #object[co.paralleluniverse.pulsar.core$promise$reify__20356 0x2f532e57 {:status :ready, 
;;                                                                                                :val #object[co.paralleluniverse.strands.channels.TransferChannel 0x534a7258 "co.paralleluniverse.strands.channels.TransferChannel@534a7258"]}], 
;;  ;:server :immutant, 
;;  :user-socket #object[co.paralleluniverse.strands.channels.TransferChannel 0x534a7258 "co.paralleluniverse.strands.channels.TransferChannel@534a7258"], 
;;  ;:auth-result [:success "Login success!" "pete"], 
;;  ;:auth-success true, 
;;  ;:user-msg "Login success!", 
;;  :user-id "pete"}
;;
;; {:on-close-msg  on-close-msg
;;  :on-error-err  on-error-err
;;  :ws-conns      ws-conns
;;  :event-queue   event-queue}


;; #_(defn init-ws-user! [{:keys [user-socket user-id ch-incoming ws-conns event-queue]}]
;;   (let [incoming-socket-source ch-incoming 
;;         outgoing-socket-sink   user-socket
;;
;;         incoming-actor (spawn ,,,)
;;         outgoing-actor nil]
;;
;;     (swap! ws-conns conj {:user-id        user-id
;;                           :socket         user-socket 
;;                           :incoming-actor incoming-actor
;;                           :outgoing-actor outgoing-actor})))

(defn immut-ws-setup []
  "Returns immut-cbs map and a map of related in-channel and promises
  that connect the Immutant API with the app."

  ;; The following channel and promises will connect the immutant api with the app
  (let [ch-incoming (channel 16 :displace true true) ;; Receives incoming user msgs. Will never block. Should not overflow/drop messages as upstream consumer batches messages. 
        ;; ch-incoming (a/chan (sliding-buffer 16)) 
        [on-open-user-socket on-close-msg on-error-err] (repeatedly 3 p/promise) 

        immut-cbs {:on-open    (fn [user-socket] (deliver on-open-user-socket user-socket)) 
                   :on-close   (fn [_ ex] (deliver on-close-msg ex))
                   :on-error   (fn [_ e]  (deliver on-error-err e))
                   :on-message (fn [_ msg] (snd ch-incoming msg))} ;; Feed all incoming msgs into buffered dropping channel - will never block 

        ws-user-args {:ch-incoming         ch-incoming
                      :on-open-user-socket on-open-user-socket
                      :on-close-msg        on-close-msg 
                      :on-error-err        on-error-err
                      :server              :immutant
                      :server-snd-fn       async/send! 
                      :server-close-fn     async/close}]

    [immut-cbs ws-user-args]))

;; (defn connect-auth-init! [ws-user-args]
;;   (some-> ws-user-args 
;;           (connect-process 200) ;; wait for connection and assoc user-socket
;;           (auth-process async/send! async/close 200) ;; returns augmented init-ws-user-args or nil
;;
;;           (select-keys [:user-id :user-socket :ch-incoming])
;;           (merge init-ws-user-args {:on-close-msg  on-close-msg
;;                                     :on-error-err  on-error-err})
;;           #_init-ws-user!))



;; -------------------------------------------------------------------------------

(defn make-handler [init-ws-user-args]
  (fn ws-handler [request]  ;; client requests a ws connection here

    (let [[immut-cbs ws-user-args] (immut-ws-setup)]

      (spawn-fiber init-ws-user/connect-auth-init! (merge init-ws-user-args ws-user-args)) 
      (async/as-channel request immut-cbs)))) ;; Does not block. Returns ring response. Could use user-socket in response :body 



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
;; (deliver on-open-user-socket user-socket)
;; (future (snd ch {:cmd [:auth {:user-id "pete" :pw "abc"}]}))
;; (deref fib-rt)
;; =>
;; {:ch-incoming #object[co.paralleluniverse.strands.channels.TransferChannel 0x55217f49 "co.paralleluniverse.strands.channels.TransferChannel@55217f49"], 
;;  :on-open-user-socket #object[co.paralleluniverse.pulsar.core$promise$reify__20356 0x2f532e57 {:status :ready, 
;;                                                                                                :val #object[co.paralleluniverse.strands.channels.TransferChannel 0x534a7258 "co.paralleluniverse.strands.channels.TransferChannel@534a7258"]}], 
;;  :server :immutant, 
;;  :user-socket #object[co.paralleluniverse.strands.channels.TransferChannel 0x534a7258 "co.paralleluniverse.strands.channels.TransferChannel@534a7258"], 
;;  :auth-result [:success "Login success!" "pete"], 
;;  :auth-success true, 
;;  :user-msg "Login success!", 
;;  :user-id "pete"}


(defrecord Ws-Handler-Immutant-main [conf ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (let [init-ws-user-args (merge conf {:ws-conns     ws-conns
                                         :event-queue  event-queue})] 
      (assoc component :ws-handler (make-handler init-ws-user-args))))

  ;; (assoc component :ws-handler nil #_(make-handler init-ws-user-args))

  (stop [component] component))



