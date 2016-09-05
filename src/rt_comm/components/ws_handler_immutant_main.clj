(ns rt-comm.components.ws-handler-immutant-main
  (:require [rt-comm.auth :refer [check-authentification non-websocket-request]]  

            [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv sfn defsfn snd join spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [taoensso.timbre :refer [debug info error spy]]
            ))


#_(defn init-ws-user! [user-id user-socket ws-conns ev-queue]
  (let [incoming-socket-source (s/->source user-socket) 
        outgoing-socket-sink   (s/->sink user-socket) 
        incoming-actor (spawn ,,,)
        outgoing-actor nil]

    (swap! ws-conns conj {:user-id        user-id
                          :socket         user-socket
                          :incoming-actor incoming-actor
                          :outgoing-actor nil})))


#_(def auth-actor 
  "ws-client-incoming-actor"
  (sfn auth-actor [ev-queue]
       (loop [aa 123]

         (receive
           [:append! new-events] (do
                                   (println "eins")
                                   (recur 123))
           ))))

:user-id weg

(defn auth-process [keys ..] 
  (let [auth-result (p/promise #(-> (rcv ch-incoming 10000 :ms) ;; Wait for first message/auth-message!
                                    (valp some? :timed-out)  ;; nil -> :timed-out
                                    check-authentification)) ;; Returns :failed, [:success user-id], .. 
        ]
    (fiber (match [@auth-result] ;; Handle outcome of auth process
                  [[:success user-id]] (do (async/send! @on-open-user-socket "Login success!") 
                                           (info (format "Ws user-id %s loged in" user-id))
                                           (-> init-ws-user-args 
                                               (assoc :user-id user-id)
                                               (assoc :user-socket-outgoing @on-open-user-socket)
                                               init-ws-user!)) 
                  [[_ user-msg]]       (do (async/send! @on-open-user-socket user-msg) ;; Failed outcomes: :timed-out :no-auth-cmd :failed 
                                           (info (format "Ws-auth attempt failed: %s" user-id))
                                           (async/close @on-open-user-socket))))))

;; -------------------------------------------------------------------------------

(defn make-handler [ws-conns event-queue]
  (fn [request]  ;; client requests a ws connection here

    (let [ch-incoming (channel 16 :displace true true) ;; Receives incoming user msgs. Will never block. Should not overflow/drop messages as upstream consumer batches messages. 
          [on-open-user-socket on-close-msg on-error-err] (repeatedly 3 p/promise) 

          init-ws-user-args {:user-id              nil ;; Will be provide by @auth-result
                             :user-socket-outgoing nil ;; Will be provide by @on-open-user-socket
                             :ch-incoming  ch-incoming
                             :on-close-msg on-close-msg
                             :on-error-err on-error-err
                             :ws-conns     ws-conns
                             :event-queue  event-queue}

          immut-cbs {:on-open    (fn [user-socket] (deliver on-open-user-socket user-socket)) 
                     :on-close   (fn [_ ex] (deliver on-close-msg ex))
                     :on-error   (fn [_ e]  (deliver on-error-err e))
                     :on-message (fn [_ msg] (snd ch-incoming msg))} ;; Feed all incoming msgs into buffered dropping channel - will never block 

          auth-result (p/promise #(-> (rcv ch-incoming 10000 :ms) ;; Wait for first message/auth-message!
                                      (valp some? :timed-out)  ;; nil -> :timed-out
                                      check-authentification)) ;; Returns :failed, [:success user-id], .. 
          
          _exec-outc  (fiber (match [@auth-result] ;; Handle outcome of auth process
                                    [[:success user-id]] (do (async/send! @on-open-user-socket "Login success!") 
                                                             (info (format "Ws user-id %s loged in" user-id))
                                                             (-> init-ws-user-args 
                                                                 (assoc :user-id user-id)
                                                                 (assoc :user-socket-outgoing @on-open-user-socket)
                                                                 init-ws-user!)) 
                                    [[_ user-msg]]       (do (async/send! @on-open-user-socket user-msg) ;; Failed outcomes: :timed-out :no-auth-cmd :failed 
                                                             (info (format "Ws-auth attempt failed: %s" user-id))
                                                             (async/close @on-open-user-socket))))
          ]
      (async/as-channel request immut-cbs) ;; Does not block. Returns ring response. Could use user-socket in response :body 
      )))



(defrecord Ws-Handler-Immutant-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler [] (make-handler ws-conns event-queue)))

  (stop [component] component))



