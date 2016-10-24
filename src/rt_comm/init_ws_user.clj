(ns rt-comm.init-ws-user
  (:require [rt-comm.incoming.connect-auth :as conn-auth] 

            [rt-comm.incoming.ws-user-pulsar    :as ws-user-pulsar]
            [rt-comm.incoming.ws-user-coreasync :as ws-user-coreasync]
            [rt-comm.incoming.ws-user-manifold  :as ws-user-manifold]

            [rt-comm.incoming.stateless-transform :as stateless-transf]

            [rt-comm.utils.utils :as u :refer [cond= valp]]
            [rt-comm.utils.async :as au]

            [manifold.deferred :as d]
            [manifold.stream :as s]
            [clojure.core.async :as a] ;; for testing only ->
            [clojure.test :as t :refer [is are run-all-tests run-tests deftest testing]]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [! spawn]]

            [rt-comm.components.event-queue :as eq] ;; testing only!

            [taoensso.timbre :refer [debug info error spy]]))




(defn init-ws-user! [{:keys [user-socket ch-incoming event-queue] :as args}]
  ;; (info "args:" args)

  (let [in-tx           (-> (select-keys args [:user-id :allowed-actns])
                            stateless-transf/incoming-tx) 

        incom-tx-stream (case (au/ch-type ch-incoming)
                          :pulsar    (au/transform-pch ch-incoming in-tx) 
                          :coreasync (au/transform-ch  ch-incoming in-tx)
                          :manifold  (s/transform in-tx ch-incoming))

        incoming-actor (case (au/ch-type ch-incoming)
                          :pulsar    (ws-user-pulsar/incoming-ws-user-actor 
                                       incom-tx-stream #(! event-queue %) (select-keys args [:batch-sample-intv])) 
                          :coreasync (ws-user-coreasync/incoming-ws-user-actor 
                                       incom-tx-stream #(! event-queue %) (select-keys args [:batch-sample-intv])) 
                          :manifold  (ws-user-manifold/incoming-ws-user-actor 
                                       incom-tx-stream #(! event-queue %) (select-keys args [:batch-sample-intv])))

        outgoing-actor nil]

    (swap! (:ws-conns args) conj {:user-id        (:user-id args)
                                  :socket         user-socket ;; debug only?!
                                  :incoming-actor incoming-actor
                                  :outgoing-actor outgoing-actor})))

;; TEST CODE:
;; (def ev-queue (spawn eq/server-actor [] 10))

;; (def user-socket (s/stream 4))
;; (def user-socket (a/chan 4))
;; (def user-socket (p/channel 4))
;;
;; (def ws-conns (init-ws-user! {:user-socket user-socket
;;                               :ch-incoming user-socket
;;                               :event-queue ev-queue
;;                               :user-id "pete"
;;                               :allowed-actns [:aa :bb]
;;                               :batch-sample-intv 0
;;                               :ws-conns (atom [])
;;                               }))
;;
;; (def cmd-ch (:incoming-actor (first ws-conns)))
;;
;; (s/put! user-socket [{:actn :aea :idx 12} 
;;                      {:actn :aa :idx 13} 
;;                      {:idx 14}])
;; (s/put! user-socket [{:actn :ada :idx 12} 
;;                      {:idx 14}])
;;
;; (a/>!! user-socket [{:actn :aea :idx 12} 
;;                     {:actn :aa :idx 13} 
;;                     {:idx 14}])
;; (a/>!! user-socket [{:actn :ada :idx 12} 
;;                     {:idx 14}])
;;
;;
;; (s/put! cmd-ch [:maintained-tags #{:ach :bch}])
;; (s/put! cmd-ch [:maintained-tags nil])
;; (s/put! cmd-ch :pause-rcv-overflow)
;; (s/put! cmd-ch :resume-rcv)
;;
;; (a/>!! cmd-ch [:maintained-tags #{:ach :bch}])
;; (a/>!! cmd-ch [:maintained-tags nil])
;; (a/>!! cmd-ch :pause-rcv-overflow)
;; (a/>!! cmd-ch :resume-rcv)
;;
;;
;; (def res (promise))
;; (s/put! cmd-ch [:debug-prc-cnt res])
;; (deref res)
;;
;; (s/put! cmd-ch [:shut-down "Buye!"])
;; (a/>!! cmd-ch [:shut-down "Buye!"])
;;
;; (info "------")

(defn load-user-data [m]
  (->> (u/load-config "dev/resources/user-data.edn")
       (assoc m :user-data)))
;; TODO should this be async?


(defsfn connect-auth-init! [ws-user-args timeouts]
  "Run connect- and auth process in sequence, conditionally call init-ws-user!"
  (some-> ws-user-args 
          (conn-auth/connect-process timeouts) ;; wait for connection and assoc user-socket
          (cond= :server :aleph #(assoc % :ch-incoming (-> % :user-socket s/->source))) ;; with aleph the user-socket is also the ch-incoming
          load-user-data
          (conn-auth/auth-process timeouts) ;; returns augmented init-ws-user-args or nil

          init-ws-user!))

;; TEST-CODE: Immutant
;; (do 
;; (def !calls (atom []))
;; (def ev-queue (spawn eq/server-actor [] 10))
;; (def args {:server-snd-fn   (fn [ch msg] (swap! !calls conj msg))
;;            :server-close-fn (fn [ch] (swap! !calls conj "closed!"))
;;            :ws-conns     (atom [])
;;            :event-queue  ev-queue 
;;            :allowed-actns [:aa :bb]
;;            :batch-sample-intv 0
;;            })
;;
;; (def ch-incoming (a/chan))
;; (def on-open-user-socket (promise))
;; (def user-socket (a/chan))
;;
;; (def fb (fiber (connect-auth-init! (merge args {:ch-incoming          ch-incoming
;;                                                 :on-open-user-socket  on-open-user-socket
;;                                                 :server :immutant}) 
;;                                    3000)))
;; )
;;
;; (deliver on-open-user-socket user-socket) ;; connect event
;; (a/>!! ch-incoming {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; immutant
;; (:auth-result (join fb))
;; (deref !calls)
;;
;; {:ch-incoming #object[clojure.core.async.impl.channels.ManyToManyChannel 0x73c0329b "clojure.core.async.impl.channels.ManyToManyChannel@73c0329b"], 
;;  :user-msg "Login success!", 
;;  :server-snd-fn #object[rt_comm.init_ws_user$fn__52373 0x19a4b33 "rt_comm.init_ws_user$fn__52373@19a4b33"], 
;;  :server :immutant, 
;;  :auth-success true, 
;;  :ws-conns #object[clojure.lang.Atom 0x362df41f {:status :ready, 
;;                                                  :val []}], 
;;  :user-id "pete", 
;;  :on-open-user-socket #object[clojure.core$promise$reify__7005 0x2ad81c28 {:status :ready, 
;;                                                                            :val #object[clojure.core.async.impl.channels.ManyToManyChannel 0x66a3b2ce "clojure.core.async.impl.channels.ManyToManyChannel@66a3b2ce"]}], 
;;  :server-close-fn #object[rt_comm.init_ws_user$fn__52375 0x49626b1c "rt_comm.init_ws_user$fn__52375@49626b1c"], 
;;  :user-data [{:user-id "pete", 
;;               :pw "abc"} {:user-id "paul", 
;;                           :pw "cde"} {:user-id "mary", 
;;                                       :pw "fgh"}], 
;;  :auth-result [:success "Login success!" "pete"], 
;;  :user-socket #object[clojure.core.async.impl.channels.ManyToManyChannel 0x66a3b2ce "clojure.core.async.impl.channels.ManyToManyChannel@66a3b2ce"], 
;;  :event-queue #object[co.paralleluniverse.actors.ActorRef 0x1a7262d0 "ActorRef@1a7262d0{PulsarActor@4326e08d[owner: fiber-10000106]}"]}

;; TEST-CODE: Aleph
;; (do 
;; (def !calls (atom []))
;; (def args {:server-snd-fn   (fn [ch msg] (swap! !calls conj msg))
;;            :server-close-fn (fn [ch] (swap! !calls conj "closed!"))})
;;
;; (def on-open-user-socket (d/deferred))
;; (def user-socket (s/stream))
;;
;; (def fb (fiber (connect-auth-init! (merge args {:on-open-user-socket  on-open-user-socket
;;                                                 :server :aleph}) 
;;                                    3000)))
;; )
;;
;; (deliver on-open-user-socket user-socket) ;; connect event
;; (s/put! user-socket {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; immutant
;; ;; (s/put! user-socket {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; aleph
;; (:auth-result (join fb))
;; (deref !calls)


