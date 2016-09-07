(ns rt-comm.components.ws-handler-immutant-main
  (:require [rt-comm.auth :refer [check-authentification non-websocket-request]]  
            [rt-comm.utils.utils :refer [valp]]

            [com.stuartsierra.component :as component] 
            [immutant.web.async :as async]

            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [taoensso.timbre :refer [debug info error spy]]
            ))
;;
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
;;
;; (check-auth-from-chan (:ch-incoming init-ws-user-args) 10000)
;;
;;
;;
;; (defn check-auth-from-chan [ch timeout] 
;;   "Return [:auth-outcome 'message'] after receiving msg on ch.
;;   Potentially blocking."
;;   (-> (rcv ch timeout :ms) ;; Wait for first message/auth-message!
;;       (valp some? :timed-out)  ;; nil -> :timed-out
;;       check-authentification))
;;
;;
;; (defn check-auth-from-chan [timeout {:keys [ch-incoming] :as m}] 
;;   "Return [:auth-outcome 'message'] after receiving msg on ch.
;;   Potentially blocking.
;;   Expects :ch-incoming in m, adds :auth-result to m"
;;   (->> (-> (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-message!
;;            (valp some? :timed-out)  ;; nil -> :timed-out
;;            check-authentification)
;;        (assoc m :auth-result)))
;;
;; (assoc m 
;;        :auth-result 
;;        (-> (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-message!
;;            (valp some? :timed-out)  ;; nil -> :timed-out
;;            check-authentification))
;;
;; (update )
;;
;; auth-result (spawn-fiber check-auth-from-chan ch-in 10000)
;; auth?-user-id ()
;;
;; (->> {:aa 12 :bb 23} 
;;      (spawn-fiber check-auth-from-chan 10000)
;;      ())
;;
;; (-> {:aa 12 :bb 23}
;;     (assoc :cc 44)
;;     (eins ))
;;
;;
;; ;; Test check-auth-from-chan blocking function 
;; (def ch-in (channel 16 :displace true true)) ;; test chan
;; (def auth-res    (spawn-fiber check-auth-from-chan ch-in 10000)) ;; Call the function with two args in a FIBER!
;; (def on-auth-res (p/promise #(check-auth-from-chan ch-in 10000)))
;;
;; (future (info "fib:" (join auth-res))) ;; deref the fiber - blocking - here in another thread
;; (future (info "fib:" @auth-res)) ;; deref the fiber - blocking - here in another thread
;; (snd ch-in {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; pass in the auth command
;;
;;
;; (defn auth?-id-or-msg [on-auth-res user-args]
;;   "On successful auth, assoc user-id, else assoc assoc user-msg."
;;   (match [@on-auth-res] ;; Handle outcome of auth process
;;          [[:success user-id]] (assoc user-args :auth-success true  :user-id  user-id)
;;          [[_ user-msg]]       (assoc user-args :auth-success false :user-msg user-msg)))
;;
;; (defn auth-process [{:keys [ch-incoming on-open-user-socket] :as init-ws-user-args}] 
;;   "Authorize using first message .."
;;   (let [auth-result (p/promise #(-> (rcv ch-incoming 10000 :ms) ;; Wait for first message/auth-message!
;;                                     (valp some? :timed-out)  ;; nil -> :timed-out
;;                                     check-authentification)) ;; Returns :failed, [:success user-id], .. 
;;         ]
;;     (fiber (match [@auth-result] ;; Handle outcome of auth process
;;                   [[:success user-id]] (do (async/send! @on-open-user-socket "Login success!") 
;;                                            (info (format "Ws user-id %s loged in" user-id))
;;                                            (-> init-ws-user-args
;;                                                (assoc :user-id user-id)
;;                                                (assoc :user-socket-outgoing @on-open-user-socket)
;;                                                init-ws-user!))
;;                   [[_ user-msg]]       (do (async/send! @on-open-user-socket user-msg) ;; Failed outcomes: :timed-out :no-auth-cmd :failed 
;;                                            (info (format "Ws-auth attempt failed: %s" user-msg))
;;                                            (async/close @on-open-user-socket))))))
;;
;;
;; ;; -------------------------------------------------------------------------------
;;
;; (defn make-handler [ws-conns event-queue]
;;   (fn [request]  ;; client requests a ws connection here
;;
;;     (let [ch-incoming (channel 16 :displace true true) ;; Receives incoming user msgs. Will never block. Should not overflow/drop messages as upstream consumer batches messages. 
;;           [on-open-user-socket on-close-msg on-error-err] (repeatedly 3 p/promise) 
;;
;;           init-ws-user-args {; :user-id              nil ;; Will be provide by @auth-result
;;                              ; :user-socket-outgoing nil ;; Will be provide by @on-open-user-socket
;;                              :ch-incoming          ch-incoming
;;                              :on-open-user-socket  on-open-user-socket
;;                              :on-close-msg         on-close-msg
;;                              :on-error-err         on-error-err
;;                              :ws-conns             ws-conns
;;                              :event-queue          event-queue}
;;
;;           immut-cbs {:on-open    (fn [user-socket] (deliver on-open-user-socket user-socket)) 
;;                      :on-close   (fn [_ ex] (deliver on-close-msg ex))
;;                      :on-error   (fn [_ e]  (deliver on-error-err e))
;;                      :on-message (fn [_ msg] (snd ch-incoming msg))} ;; Feed all incoming msgs into buffered dropping channel - will never block 
;;           ]
;;       ()
;;       (async/as-channel request immut-cbs) ;; Does not block. Returns ring response. Could use user-socket in response :body 
;;       )))
;;


(defrecord Ws-Handler-Immutant-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler [] #_(make-handler ws-conns event-queue)))

  (stop [component] component))



