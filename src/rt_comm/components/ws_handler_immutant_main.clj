(ns rt-comm.components.ws-handler-immutant-main
  (:require [rt-comm.auth :refer [check-authentification non-websocket-request]]  
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

;; (defn check-auth-from-chan [timeout {:keys [ch-incoming] :as m}] 
;;   "Produce promise/fiber of [:auth-outcome 'message'] after receiving msg on ch.
;;   Expects :ch-incoming in m, adds :on-auth-res to m."
;;   (->> (-> (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-message!
;;            (valp some? :timed-out)  ;; nil -> :timed-out
;;            check-authentification)
;;        (assoc m :auth-result)))

(defn check-auth-from-chan [{:keys [ch-incoming] :as m} timeout] 
  "Produce promise/fiber of [:auth-outcome 'message'] after receiving msg on ch.
  Expects :ch-incoming in m, adds :on-auth-res to m."
  (let [auth-msg   (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-messag
        auth-msg-t (valp auth-msg some? :timed-out)  ;; nil -> :timed-out
        auth-outc  (check-authentification auth-msg-t)]
    (assoc m :auth-result auth-outc)))

;; TEST-CODE:
;; (def ch1 (channel))
;; (def fu1 (future (check-auth-from-chan 5000 {:ch-incoming ch1})))
;; (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; pass in the auth command
;; (deref fu1)

;; Access arg map via deref - no destructuring
;; 
(defn auth-success-args [m]
     "On successful auth, assoc user-id, else assoc user-msg."
     (match [(:auth-result @m)] ;; Handle outcome of auth process
            [[:success user-msg user-id]] (assoc @m :auth-success true  :user-msg user-msg :user-id  user-id)
            [[_        user-msg]]         (assoc @m :auth-success false :user-msg user-msg)))

;; TEST-CODE:
;; (auth-success-args 
;;   (future {:auth-result [:success "peter"]}))
;;
;; (def p1 (promise))
;; (future (info (auth-success-args p1)))
;; (deliver p1 {:auth-result [:success "peter"]})

;; TEST-CODE:
;; (def ch1 (channel))
;; (def pr2 (->> {:ch-incoming ch1} 
;;               (partial check-auth-from-chan 5000) ;; Create fn that needs no args
;;               p/promise ;; run it in a promise
;;               (partial auth-success-args) ;; Use the promise as last argument of another fn
;;               p/promise ;; run that fn (which uses the first promise) in another promise
;;               ))
;; (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; pass in the auth command

;; (defn in-promise [f & args]
;;   (-> (apply partial f args) p/promise))


(defn send-user-msg! [m send-fn]
  (send-fn (:user-socket-outgoing @m) (:user-msg @m))
  @m)

(defn log-auth-success [m] 
  (if (:auth-success @m)
    (info (format "Ws user-id %s loged in!"    (:user-id @m)))
    (info (format "Ws-auth attempt failed: %s" (:user-msg @m))))
  @m)


(defn close-or-pass [m close-fn]
  (if-not (:auth-success @m)
    (do (close-fn (:user-socket-outgoing @m)) nil)) ;; Return nil on failure
  @m)


(defn in-promise [co f & args]
  (-> (apply partial f co args) p/promise))


;; Pass close and send functions to use with other servers
(defn auth-process [init-ws-user-args send-fn close-fn]
  (-> init-ws-user-args 
      (in-promise check-auth-from-chan 5000)
      (in-promise auth-success-args)
      (in-promise #(assoc :user-socket-outgoing @(:on-open-user-socket @%)))
      (in-promise send-user-msg! send-fn)
      (in-promise log-auth-success)
      (in-promise close-or-pass close-fn)))

(defn auth-process [init-ws-user-args send-fn close-fn]
  (-> init-ws-user-args 
      (check-auth-from-chan 5000)
      auth-success-args
      #(assoc :user-socket-outgoing @(:on-open-user-socket %))
      (send-user-msg! send-fn)
      log-auth-success
      (close-or-pass close-fn)))



;; TEST-CODE
;; (def ch1 (channel))
;; (def pr2 (-> {:ch-incoming ch1}
;;              (in-promise check-auth-from-chan 5000)
;;              (in-promise auth-success-args)
;;              (in-promise #(assoc :user-socket-outgoing @(:on-open-user-socket @%)))
;;              (in-promise send-user-msg!)
;;              (in-promise log-auth-success)
;;              (in-promise close-or-pass)
;;              ))
;; (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]})

;; Previous implementation
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


;; -------------------------------------------------------------------------------

(defn make-handler [ws-conns event-queue]
  (fn [request]  ;; client requests a ws connection here

    (let [ch-incoming (channel 16 :displace true true) ;; Receives incoming user msgs. Will never block. Should not overflow/drop messages as upstream consumer batches messages. 
          [on-open-user-socket on-close-msg on-error-err] (repeatedly 3 p/promise) 

          init-ws-user-args {; :user-id              nil ;; Will be provide by @auth-result
                             ; :user-socket-outgoing nil ;; Will be provide by @on-open-user-socket
                             :ch-incoming          ch-incoming
                             :on-open-user-socket  on-open-user-socket
                             :on-close-msg         on-close-msg
                             :on-error-err         on-error-err
                             :ws-conns             ws-conns
                             :event-queue          event-queue}

          immut-cbs {:on-open    (fn [user-socket] (deliver on-open-user-socket user-socket)) 
                     :on-close   (fn [_ ex] (deliver on-close-msg ex))
                     :on-error   (fn [_ e]  (deliver on-error-err e))
                     :on-message (fn [_ msg] (snd ch-incoming msg))} ;; Feed all incoming msgs into buffered dropping channel - will never block 
          ]
      (some-> init-ws-user-args 
              auth-process  ;; returns promise of augmented init-ws-user-args
              init-ws-user!)
      (async/as-channel request immut-cbs) ;; Does not block. Returns ring response. Could use user-socket in response :body 
      )))



(defrecord Ws-Handler-Immutant-main [ws-conns event-queue ws-handler]
  component/Lifecycle

  (start [component]
    (assoc component :ws-handler [] #_(make-handler ws-conns event-queue)))

  (stop [component] component))



