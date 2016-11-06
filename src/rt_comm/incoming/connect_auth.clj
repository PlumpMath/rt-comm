(ns rt-comm.incoming.connect-auth
  (:require [clojure.core.match :refer [match]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [clojure.core.async :as a]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.async :as au :refer [await-deref await-<!]] 
            [rt-comm.utils.utils :as u :refer [valp cond=]]))


(defn auth-msg 
  "Return first msg from ch [pulsar, core.async or manifold!] or :timed-out/:conn-error." 
  [ch timeout]
  (case (au/ch-type ch)
    :pulsar    (-> (rcv ch timeout :ms) ;; wait for first message/auth-message!
                   (valp some? :timed-out)) ;; nil -> :timed-out

    :coreasync (-> (a/go (a/alts! [ch (a/timeout timeout)]))
                   await-<!
                   first
                   (valp some? :timed-out))

    :manifold  (-> (s/take! ch)
                   (d/timeout! timeout :timed-out)
                   (d/catch (fn [e] :conn-error))
                   await-deref)))

;; TEST-CODE:
;; (def ch1 (channel))
;; (def fu1 (fiber (auth-msg ch1 4000)))
;; (future (snd ch1 {:actn :auth :data {:user-id "pete" :pw "abc"}})) ;; pass in the auth command
;; (deref fu1)
;;
;; (def ch1 (a/chan))
;; (def fu1 (fiber (auth-msg ch1 4000)))
;; (future (a/>!! ch1 {:actn :auth :data {:user-id "pete" :pw "abc"}})) ;; pass in the auth command
;; (deref fu1)
;;
;; (def ch1 (s/stream))
;; (def fu1 (fiber (auth-msg ch1 4000)))
;; (s/put! ch1 {:actn :auth :data {:user-id "pete" :pw "abc"}}) ;; pass in the auth command
;; (deref fu1)


(defn auth-result 
  "Checks auth-msg + included login, returns [:auth-outcome 'message' user-id]."
  [auth-msg user-data]
  (match [auth-msg] 
       [{:actn :auth 
         :data login}] (if (u/contains-el? login user-data) 
                         [:success "Login success!" (:user-id login)] 
                         [:failed  "Login failed! Disconnecting."])

       [:timed-out]      [:timed-out "Authentification timed out! Disconnecting."] 
       [auth-ms]         [:no-auth-cmd (format "Expected auth-command but got: %s. Disconnecting." auth-ms)] 
       :else             [:no-auth-cmd "Expected auth. command not found! Disconnecting."]))


;; TEST-CODE:
;; (def user-data [{:user-id "pete" :pw "abc"} 
;;                 {:user-id "paul" :pw "cde"} 
;;                 {:user-id "mary" :pw "fgh"}])
;; (auth-result {:actn :auth :data {:user-id "pete" :pw "abc"}} user-data)
;; (auth-result {:actn :auth :data {:user-id "pete" :pw "abc"}} user-data)
;; (auth-result :timed-out user-data)


(defn auth-success-args 
  "Derive :auth-success, :user-msg and :user-id props from :auth-result."
  [m]
  (match [(:auth-result m)] ;; Handle outcome of auth process
         [[:success user-msg user-id]] (assoc m :auth-success true  :user-msg user-msg :user-id  user-id)
         [[_        user-msg]]         (assoc m :auth-success false :user-msg user-msg)))



(defn send-user-msg! [m]
  ((:server-snd-fn m) (:user-socket m) (:user-msg m)) ;; send-fn is async/non-blocking/fire and forget. TODO: could evaluate options/on-success async outcome
  m)

(defn send-user-msg-async! [send-fn {:keys [user-socket user-msg] :as m} cb]
  (let [cb-s (fn [_] (-> (assoc m :send-user-success true) cb)) 
        cb-e (fn [e] (-> (assoc m :send-user-success false :send-user-error e) cb))] 
    (case (:server m)
      :immutant (send-fn user-socket user-msg {:on-success cb-s
                                               :on-error cb-e}) 
      :aleph    (-> (send-fn user-socket user-msg)
                    (d/on-realized cb-s cb-e)))))

(defn log-auth-success [m] 
  (if (:auth-success m)
    (info (format "Ws user-id %s logged in!"   (:user-id m)))
    (info (format "Ws-auth attempt failed: %s" (:user-msg m))))
  m)

(defn close-or-pass! [m]
  (if-not (:auth-success m)
    (do ((:server-close-fn m) (:user-socket m)) nil) ;; Return nil on failure 
    m))    


(defsfn auth-process 
  "Wait for auth cmd, add user-id, send user msg, on failure
  disconnect and return nil."
  [args]
  (-> (auth-msg (:ch-incoming args) (:auth-timeout args)) ;; pause fiber
      (auth-result (:user-data args))
      (->> (assoc args :auth-result)) 
      auth-success-args
      send-user-msg! ;; non-blocking
      ;; (->> (p/await send-user-msg-async! send-fn))  ;; TODO: test this!
      log-auth-success
      close-or-pass!))


(defsfn connect-process 
  "Assoc :user-socket from connection promise [or deferred]
  within timeout or return nil"
  [{:keys [on-open-user-socket connect-timeout] :as m}]
  (let [user-socket (-> on-open-user-socket
                        (au/timeout! connect-timeout nil) ;; starts another fiber that will pause and deliver after timeout
                        ;; (d/chain dec #(/ 1 %)) ;; TEST CODE: Raise error
                        (d/catch (fn [e] (error "Ws connection error:" e) nil)) ;; swallow potential Aleph error, return nil
                        await-deref)]
    (when user-socket
      (-> (assoc m :user-socket user-socket)))))

;; TEST-CODE
;; (def p1 (p/promise))
;; (def p1 (d/deferred))
;; (def f1 (fiber (connect-process {:on-open-user-socket p1} 3000)))
;; (deliver p1 2)
;; (deref f1)






