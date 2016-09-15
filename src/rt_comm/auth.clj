(ns rt-comm.auth
  (:require [clojure.core.match :refer [match]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer [valp]]
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


(deftest auth1
  (testing "check-authentification" 
    (are [m res] (= (check-authentification m) res)
         {:cmd [:auth {:user-id "pete" :pw "abc"}]} [:success "Login success!" "pete"] 
         {:cmd [:auth {:user-id "pete" :pw "abd"}]} [:failed  "User-id - password login failed! Disconnecting."] 
         :timed-out [:timed-out "Authentification timed out! Disconnecting."]))

  (testing "check-auth-from-chan immutant" 
    (let [do-auth-immut (fn [cmd wait]
                          (let [ch (channel)
                                fu (future (check-auth-from-chan-immut {:ch-incoming ch} 20))] ;; Start the auth process
                            (sleep wait)
                            (future (snd ch cmd)) ;; Send first/auth message 
                            (:auth-result @fu)))]
      (is (= (do-auth-immut {:cmd [:auth {:user-id "pete" :pw "abc"}]} 10) 
             [:success "Login success!" "pete"]))
      (is (= (do-auth-immut {:cmd [:auth {:user-id "pete" :pw "abc"}]} 30) 
             [:timed-out "Authentification timed out! Disconnecting."]))))

  (testing "check-auth-from-chan aleph" 
    (let [do-auth-aleph (fn [cmd wait]
                          (let [ch (s/stream)
                                fu (future (check-auth-from-chan-aleph {:user-socket ch} 20))] ;; Start the auth process
                            (sleep wait)
                            (future (s/put! ch cmd))
                            (:auth-result @fu)))]  ;; Send first/auth message
      (is (= (do-auth-aleph {:cmd [:auth {:user-id "pete" :pw "abc"}]} 10) 
             [:success "Login success!" "pete"]))
      (is (= (do-auth-aleph {:cmd [:auth {:user-id "pete" :pw "abc"}]} 30) 
             [:timed-out "Authentification timed out! Disconnecting."])))))

;; (run-tests)

#_(defn make-handler [ws-conns event-queue]
  "The ws-handler will connect and auth the requesting ws-client and 
  then call init.."
  (fn connect-and-auth [request]  ;; client requests a ws connection here
    ;; Return a deferred -> async handler
    ;; 1. CONNECT:
    (let [conn-d (http/websocket-connection request) 
          auth   (-> (d/chain conn-d       ;; Async 1: Wait for connection
                              #(s/take! %) ;; Async 2: Wait for first message
                              check-authentification) ;; Returns :failed, [:success user-id], ..
                     (d/timeout! 10000 :timed-out) ;; Connection and auth must be competed within timeout
                     (d/catch (fn [e] :conn-error))) ;; Catch non-WS requests. Other errors? 

          send-msg-close #(d/future (do (s/put!   @conn-d %) 
                                        (s/close! @conn-d) nil))] ;; Ws-handler will return nil - see notes/question
      ;; 2. AUTH:
      (d/chain auth 
               #(match [%] ;; Handle outcome of auth process
                       [:conn-error]  non-websocket-request ;; Return Http response on error
                       [:timed-out]   (send-msg-close "Authentification timed out! Disconnecting.")
                       )))))


(defn check-auth-from-chan-aleph [{:keys [user-socket] :as m} timeout] 
  "Produce [:auth-outcome 'message' user-id] after receiving msg in :user-socket
  Expects manifold stream in m :user-socket, adds :auth-result to m."
  (-> (s/take! user-socket) ;; Wait for first message/auth-message!
      (d/timeout! timeout :timed-out) ;; Connection and auth must be competed within timeout
      (d/catch (fn [e] :conn-error))
      deref
      check-authentification
      (->> (assoc m :auth-result))))

;; TEST-CODE:
;; (def ch1 (s/stream))
;; (def fu1 (future (check-auth-from-chan-aleph {:user-socket ch1} 5000)))
;; (s/put! ch1 {:cmd [:auth {:user-id "pete" :pw "abc"}]}) ;; pass in the auth command
;; (deref fu1)


(defn check-auth-from-chan-immut [{:keys [ch-incoming] :as m} timeout] 
  "Produce [:auth-outcome 'message' user-id] after receiving msg on ch.
  Expects :ch-incoming in m, adds :auth-result to m."
  (-> (rcv ch-incoming timeout :ms) ;; Wait for first message/auth-message!
      (valp some? :timed-out)  ;; nil -> :timed-out
      check-authentification
      (->> (assoc m :auth-result))))

;; TEST-CODE:
;; (def ch1 (channel))
;; (def fu1 (future (check-auth-from-chan-immut {:ch-incoming ch1} 500)))
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

(defn send-user-msg! [m send-fn]
  (send-fn (:user-socket-outgoing m) (:user-msg m))
  m)

(defn log-auth-success! [m] 
  (if (:auth-success m)
    (info (format "Ws user-id %s logged in!"   (:user-id m)))
    (info (format "Ws-auth attempt failed: %s" (:user-msg m))))
  m)

(defn close-or-pass! [m close-fn]
  (if-not (:auth-success m)
    (do (close-fn (:user-socket-outgoing m)) nil)) ;; Return nil on failure
  m)

(defn auth-process [auth-args send-fn close-fn]
  (-> auth-args 
      
      (as-> m (case (:server m) 
                :immutant (check-auth-from-chan-immut m 5000)
                :aleph    (check-auth-from-chan-aleph m 5000))) 
      auth-success-args
      (send-user-msg! send-fn)
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

