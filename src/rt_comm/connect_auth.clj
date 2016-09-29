(ns rt-comm.connect-auth
  (:require [clojure.core.match :refer [match]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer [valp]]))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})


(def users [{:user-id "pete" :pw "abc"} 
            {:user-id "paul" :pw "cde"} 
            {:user-id "mary" :pw "fgh"}])


(defn registered-user? [login]
  (-> (partial = login) (filter users) not-empty))


(defn check-authentification [auth-message]
  (match [auth-message]
         [{:cmd [:auth login]}] (if (registered-user? login) 
                                  [:success "Login success!" (:user-id login)] 
                                  [:failed  "Login failed! Disconnecting."])

         ["test"]               [:success "test-id"] 

         [:timed-out]           [:timed-out "Authentification timed out! Disconnecting."] 
         :else                  [:no-auth-cmd "Expected auth. command not found! Disconnecting."]))


(defsfn deref* [d] 
  "Deref the given manifold.deferred (using a callback), merely
  blocking the current fiber, not the current thread."
  (if (= (type d) manifold.deferred.Deferred) 
    (p/await (fn [d cb]
               (d/on-realized d cb cb))
             d)
    (deref d)))

(defsfn check-auth-from-chan-aleph [{:keys [user-socket] :as m} timeout] 
  "Produce [:auth-outcome 'message' user-id] after receiving msg in :user-socket
  Expects manifold stream in m :user-socket, adds :auth-result to m."
  (-> (s/take! user-socket) ;; Wait for first message/auth-message! ; does not pause here - returns deferred immediately
      (d/timeout! timeout :timed-out) ;; Auth msg must be received within timeout
      (d/catch (fn [e] :conn-error))
      deref* ;; pause fiber to optain value from deferred
      check-authentification
      (->> (assoc m :auth-result))))

;; TEST-CODE:
;; (def ch1 (s/stream))
;; (def fu1 (fiber (check-auth-from-chan-aleph {:user-socket ch1} 5000)))
;; (s/put! ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; pass in the auth command
;; (deref fu1)

(defsfn check-auth-from-chan-immut [{:keys [ch-incoming] :as m} timeout] 
  "Produce [:auth-outcome 'message' user-id] after receiving msg on ch.
  Expects :ch-incoming in m, adds :auth-result to m."
  (-> (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-message! ; pauses the fiber, returns value
      (valp some? :timed-out)  ;; nil -> :timed-out
      check-authentification
      (->> (assoc m :auth-result))))

;; TEST-CODE:
;; (def ch1 (channel))
;; (def fu1 (fiber (check-auth-from-chan-immut {:ch-incoming ch1} 4000)))
;; (future (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]})) ;; pass in the auth command
;; (deref fu1)


(defn auth-success-args [m]
     "On successful auth, assoc user-id, else assoc user-msg."
     (match [(:auth-result m)] ;; Handle outcome of auth process
            [[:success user-msg user-id]] (assoc m :auth-success true  :user-msg user-msg :user-id  user-id)
            [[_        user-msg]]         (assoc m :auth-success false :user-msg user-msg)))

(defn send-user-msg! [m send-fn]
  (send-fn (:user-socket m) (:user-msg m)) ;; send-fn is async/non-blocking/fire and forget. TODO: could evaluate options/on-success async outcome
  m)

(defn bl-send-user-msg! [send-fn {:keys [user-socket user-msg] :as m} cb]
  (let [cb-s (fn [_] (-> (assoc m :send-user-success true) cb)) 
        cb-e (fn [e] (-> (assoc m :send-user-success false :send-user-error e) cb))] 
    (case (:server m)
      :immutant (send-fn user-socket user-msg {:on-success cb-s
                                               :on-error cb-e}) 
      :aleph    (-> (send-fn user-socket user-msg)
                    (d/on-realized cb-s cb-e)))))

(defn log-auth-success! [m] 
  (if (:auth-success m)
    (info (format "Ws user-id %s logged in!"   (:user-id m)))
    (info (format "Ws-auth attempt failed: %s" (:user-msg m))))
  m)

(defn close-or-pass! [m close-fn]
  (if-not (:auth-success m)
    (do (close-fn (:user-socket m)) nil)) ;; Return nil on failure
  m)


(defsfn auth-process [auth-args send-fn close-fn timeout]
  "Wait for auth cmd, add user-id and send success msg or
  disconnect and return nil."
  (-> auth-args 
      (as-> m (case (:server m)
                :immutant (check-auth-from-chan-immut m timeout)   ;; pausing fiber 
                :aleph    (check-auth-from-chan-aleph m timeout))) ;; pausing fiber
      auth-success-args
      (send-user-msg! send-fn) ;; non-blocking
      ;; (->> (p/await bl-send-user-msg! send-fn))  ;; TODO: test this!
      log-auth-success!
      (close-or-pass! close-fn)))

;; TEST-CODE
;; (def ch1 (channel))
;; (def pr2 (fiber (-> {:ch-incoming ch1}
;;                     (check-auth-from-chan 5000)
;;                     auth-success-args
;;                     log-auth-success
;;                     )))
;; (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]})


(defn timeout! [p timeout v]
  "Deliver v to p after timeout. Returns p."
  (fiber (do (sleep timeout) (deliver p v)))
  p)


(defsfn connect-process [{:keys [on-open-user-socket] :as m} timeout]
  "Assoc :user-socket from connection promise [or deferred]
  within timeout or return nil"
  (let [user-socket (-> on-open-user-socket
                        (timeout! timeout nil) ;; starts another fiber that will pause and deliver after timeout
                        ;; (d/chain dec #(/ 1 %)) ;; TEST CODE: Raise error
                        ;; (d/catch (fn [e] (error "Ws connection error:" e) nil)) ;; swallow potential Aleph error, return nil
                        deref*)]
    (when user-socket 
      (assoc m :user-socket user-socket))))

;; TEST-CODE
;; (def p1 (p/promise))
;; (def p1 (d/deferred))
;; (def f1 (fiber (connect-process {:on-open-user-socket p1} 3000)))
;; (deliver p1 2)
;; (deref f1)


