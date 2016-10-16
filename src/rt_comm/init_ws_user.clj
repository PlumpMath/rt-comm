(ns rt-comm.init-ws-user
  (:require [rt-comm.incoming.connect-auth :refer [connect-process auth-process]] 

            ;; [rt-comm.incoming.ws-user-pulsar    :refer [incoming-ws-user-actor]]
            ;; [rt-comm.incoming.ws-user-coreasync :refer [incoming-ws-user-actor]]
            [rt-comm.incoming.ws-user-manifold  :refer [incoming-ws-user-actor]]
            [rt-comm.incoming.stateless-transform :refer [incoming-tx]]

            [rt-comm.utils.utils :refer [valp]]
            [rt-comm.utils.async :as au]

            [manifold.stream :as s]
            [clojure.core.async :as a] ;; for testing only ->

            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [! spawn]]

            [rt-comm.components.event-queue :as eq] ;; testing only!

            [taoensso.timbre :refer [debug info error spy]]))


(defn connect-auth-init! [ws-user-args]
  "Suspendible fn running connect- and auth process, conditionally calling init-ws-user!"
  (some-> ws-user-args 
          (connect-process 200) ;; wait for connection and assoc user-socket
          (auth-process 200) ;; returns augmented init-ws-user-args or nil

          #_init-ws-user!))


(defn init-ws-user! [{:keys [user-socket ch-incoming event-queue] :as args}]

  (let [user-socket-in  (valp ch-incoming some? (s/->source user-socket)) 

        in-tx           (-> (select-keys args [:user-id :allowed-actns])
                            incoming-tx) 

        ;; Manifold
        ;; incom-tx-stream (s/transform in-tx user-socket-in)

        ;; core.async
        incom-tx-stream (au/transf-st-ch user-socket-in in-tx)

        ;; Pulsar
        ;; incom-tx-stream (au/transf-st-pch user-socket-in in-tx)


        ;; Pulsar
        ;; incoming-actor (spawn incoming-ws-user-actor 
        ;;                       incom-tx-stream
        ;;                       event-queue
        ;;                       (select-keys args [:batch-sample-intv]))

        ;; Manifold or core.async
        incoming-actor (incoming-ws-user-actor
                         incom-tx-stream
                         #(! event-queue %)  ;; snd-event-queue function
                         (select-keys args [:batch-sample-intv]))


        outgoing-actor nil]

    (swap! (:ws-conns args) conj {:user-id        (:user-id args)
                                  :socket         user-socket ;; debug only?!
                                  :incoming-actor incoming-actor
                                  :outgoing-actor outgoing-actor})))

;; TEST CODE:
(def ev-queue (spawn eq/server-actor [] 10))
(def user-socket (s/stream 4))
(def user-socket (a/chan 4))

(def ws-conns (init-ws-user! {:user-socket user-socket
                              :event-queue ev-queue
                              :user-id "pete"
                              :allowed-actns [:aa :bb]
                              :batch-sample-intv 0
                              :ws-conns (atom [])
                              }))

(def cmd-ch (:incoming-actor (first ws-conns)))

(s/put! user-socket [{:actn :aea :idx 12} 
                     {:actn :aa :idx 13} 
                     {:idx 14}])
(s/put! user-socket [{:actn :ada :idx 12} 
                     {:idx 14}])

(a/>!! user-socket [{:actn :aea :idx 12} 
                    {:actn :aa :idx 13} 
                    {:idx 14}])
(a/>!! user-socket [{:actn :ada :idx 12} 
                    {:idx 14}])


(s/put! cmd-ch [:fixed-recip-chs #{:ach :bch}])
(s/put! cmd-ch [:fixed-recip-chs nil])
(s/put! cmd-ch :pause-rcv-overflow)
(s/put! cmd-ch :resume-rcv)

(a/>!! cmd-ch [:fixed-recip-chs #{:ach :bch}])
(a/>!! cmd-ch [:fixed-recip-chs nil])
(a/>!! cmd-ch :pause-rcv-overflow)
(a/>!! cmd-ch :resume-rcv)


(def res (promise))
(s/put! cmd-ch [:debug-prc-cnt res])
(deref res)

(s/put! cmd-ch [:shut-down "Buye!"])

(info "------")



