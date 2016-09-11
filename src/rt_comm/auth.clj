(ns rt-comm.auth
  (:require [clojure.core.match :refer [match]]
            ))

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
                                  [:failed  "User-id - password login failed! Disconnecting."])

         ["test"]               [:success "test-id"] 

         [:timed-out]           [:timed-out "Authentification timed out! Disconnecting."] 
         :else                  [:no-auth-cmd "Expected auth. command not found! Disconnecting."]))

;; (check-authentification {:cmd [:auth {:user-id "pete" :pw "abc"}]})


(defn check-auth-from-chan [{:keys [ch-incoming] :as m} timeout] 
  "Produce [:auth-outcome 'message' user-id] after receiving msg on ch.
  Expects :ch-incoming in m, adds :auth-result to m."
  (-> (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-message!
      (valp some? :timed-out)  ;; nil -> :timed-out
      check-authentification
      (->> (assoc m :auth-result))))

;; TEST-CODE:
;; (def ch1 (channel))
;; (def fu1 (future (check-auth-from-chan 5000 {:ch-incoming ch1})))
;; (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; pass in the auth command
;; (deref fu1)

;; (defn check-auth-from-chan [{:keys [ch-incoming] :as m} timeout] 
;;   "Produce [:auth-outcome 'message'] after receiving msg on ch.
;;   Expects :ch-incoming in m, adds :auth-result to m."
;;   (let [auth-msg   (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-messag
;;         auth-msg-t (valp auth-msg some? :timed-out) ;; nil -> :timed-out
;;         auth-outc  (check-authentification auth-msg-t)]
;;     (assoc m :auth-result auth-outc)))


(defn auth-success-args [m]
     "On successful auth, assoc user-id, else assoc user-msg."
     (match [(:auth-result m)] ;; Handle outcome of auth process
            [[:success user-msg user-id]] (assoc m :auth-success true  :user-msg user-msg :user-id  user-id)
            [[_        user-msg]]         (assoc m :auth-success false :user-msg user-msg)))

;; TEST-CODE:
;; (auth-success-args 
;;   (future {:auth-result [:success "peter"]}))
;;
;; (def p1 (promise))
;; (future (info (auth-success-args p1)))
;; (deliver p1 {:auth-result [:success "peter"]})


(defn send-user-msg! [m send-fn]
  (send-fn (:user-socket-outgoing m) (:user-msg m))
  m)

(defn log-auth-success [m] 
  (if (:auth-success m)
    (info (format "Ws user-id %s logged in!"   (:user-id m)))
    (info (format "Ws-auth attempt failed: %s" (:user-msg m))))
  m)

(defn close-or-pass [m close-fn]
  (if-not (:auth-success m)
    (do (close-fn (:user-socket-outgoing m)) nil)) ;; Return nil on failure
  m)


(defn auth-process [auth-args send-fn close-fn]
  (-> auth-args 
      (check-auth-from-chan 5000)
      auth-success-args
      #(assoc :user-socket-outgoing @(:on-open-user-socket %))
      (send-user-msg! send-fn)
      log-auth-success
      (close-or-pass close-fn)))

;; TEST-CODE
;; (def ch1 (channel))
;; (def pr2 (fiber (-> {:ch-incoming ch1}
;;                     (check-auth-from-chan 5000)
;;                     auth-success-args
;;                     log-auth-success
;;                     )))
;; (snd ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]})


;; (defn in-promise [co f & args]
;;   (-> (apply partial f co args) p/promise))
;;
;; (defn auth-process [init-ws-user-args send-fn close-fn]
;;   (-> init-ws-user-args 
;;       (in-promise check-auth-from-chan 5000)
;;       (in-promise auth-success-args)
;;       (in-promise #(assoc :user-socket-outgoing @(:on-open-user-socket @%)))
;;       (in-promise send-user-msg! send-fn)
;;       (in-promise log-auth-success)
;;       (in-promise close-or-pass close-fn)))

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



