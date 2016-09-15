(ns rt-comm.components.ws-handler-immutant-main
  (:require [rt-comm.auth] 
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

#_(defn make-handler [ws-conns event-queue]
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
                     (as-> m (assoc m :user-socket @(:on-open-user-socket m))) ;; wait for connection
                     (auth-process async/send! async/close) ;; returns augmented init-ws-user-args or nil
                     (merge init-ws-user-args)
                     init-ws-user!))
      (async/as-channel request immut-cbs) ;; Does not block. Returns ring response. Could use user-socket in response :body 
      )))



(defrecord Ws-Handler-Immutant-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler [] #_(make-handler ws-conns event-queue)))

  (stop [component] component))



