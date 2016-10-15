(ns rt-comm.connect-auth-test
  (:require [clojure.core.match :refer [match]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer [valp]]

            [rt-comm.incoming.connect-auth :refer [check-authentification 
                                                   check-auth-from-chan-immut 
                                                   check-auth-from-chan-aleph
                                                   auth-process
                                                   connect-process
                                                   connect-process]]))

(deftest auth
  (testing "check-authentification" 
    (are [m res] (= (check-authentification m) res)
         {:cmd [:auth {:user-id "pete" :pw "abc"}]} [:success "Login success!" "pete"] 
         {:cmd [:auth {:user-id "pete" :pw "abd"}]} [:failed  "Login failed! Disconnecting."] 
         :timed-out [:timed-out "Authentification timed out! Disconnecting."]))

  (testing "check-auth-from-chan immutant" 
    (let [do-auth-immut (fn [cmd wait]
                          (let [ch (channel)
                                fu (fiber (check-auth-from-chan-immut {:ch-incoming ch} 20))] ;; Start the auth process
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
                                fu (fiber (check-auth-from-chan-aleph {:user-socket ch} 20))] ;; Start the auth process
                            (sleep wait)
                            (future (s/put! ch cmd))
                            (:auth-result @fu)))]  ;; Send first/auth message
      (is (= (do-auth-aleph {:cmd [:auth {:user-id "pete" :pw "abc"}]} 10) 
             [:success "Login success!" "pete"]))
      (is (= (do-auth-aleph {:cmd [:auth {:user-id "pete" :pw "abc"}]} 30) 
             [:timed-out "Authentification timed out! Disconnecting."])))))


(defn do-conn-auth-immut [wait-conn wait-auth user-socket user-id !calls]
  "Connection and auth mimicking ws-handler code"
  (let [ch (channel)
        on-open-user-socket (p/promise)
        auth-ws-user-args {:ch-incoming          ch
                           :on-open-user-socket  on-open-user-socket
                           :server :immutant}  
        send! (fn [ch msg] (swap! !calls conj msg))
        close (fn [ch] (do (p/close! ch) 
                           (swap! !calls conj "closed!")))
        ;; TESTS THIS CODE:
        fib-rt (fiber (some-> auth-ws-user-args 
                              (connect-process 200) 
                              (auth-process send! close 200)))]
    (sleep wait-conn)
    (deliver on-open-user-socket user-socket)
    (sleep wait-auth)
    (future (snd ch {:cmd [:auth {:user-id user-id :pw "abc"}]}))
    @fib-rt))

(defn do-conn-auth-aleph [wait-conn wait-auth user-socket user-id !calls]
  "Connection and auth mimicking ws-handler code"
  (let [on-open-user-socket (d/deferred)
        auth-ws-user-args {:on-open-user-socket  on-open-user-socket
                           :server :aleph}  
        send! (fn [ch msg] (swap! !calls conj msg))
        close (fn [ch] (do (s/close! ch) 
                           (swap! !calls conj "closed!")))
        ;; TESTS THIS CODE:
        fib-rt (fiber (some-> auth-ws-user-args 
                              (connect-process 200) 
                              (auth-process send! close 200)))]
    (sleep wait-conn)
    (deliver on-open-user-socket user-socket)
    (sleep wait-auth)
    (future (s/put! user-socket {:cmd [:auth {:user-id user-id :pw "abc"}]}))
    @fib-rt))


(deftest connect-auth-immutant
  (testing "Connect and auth process Immutant" 
    (testing "Connection process timeout" 
      (let [!calls (atom [])
            user-socket (channel)
            user-id "pete"
            ret (do-conn-auth-immut 250 100 user-socket user-id !calls)]
        (is (nil? ret) "yealds nil")
        (is (empty? @!calls) "sends no msgs to client")))

    (testing "Auth process timeout" 
      (let [!calls (atom [])
            user-socket (channel)
            user-id "pete"
            ret (do-conn-auth-immut 150 250 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :timed-out))
        (is (= @!calls
               ["Authentification timed out! Disconnecting." "closed!"]))
        (is (p/closed? user-socket)))) 

    (testing "Login failed" 
      (let [!calls (atom [])
            user-socket (channel)
            user-id "non-user"
            ret (do-conn-auth-immut 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :failed))
        (is (= @!calls
               ["Login failed! Disconnecting." "closed!"]))
        (is (p/closed? user-socket))))

    (testing "Login succeeded" 
      (let [!calls (atom [])
            user-socket (channel)
            user-id "pete"
            ret (do-conn-auth-immut 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               true))
        (is (= (-> ret :auth-result)
               [:success "Login success!" user-id]))
        (is (= @!calls
               ["Login success!"]))
        (is (-> user-socket p/closed? not))))))


(deftest conn-auth-aleph 
  (testing "Connect and auth process Aleph" 
    (testing "Connection process timeout" 
      (let [!calls (atom [])
            user-socket (s/stream)
            user-id "pete"
            ret (do-conn-auth-aleph 250 100 user-socket user-id !calls)]
        (is (nil? ret) "yealds nil")
        (is (empty? @!calls) "sends no msgs to client")))

    (testing "Auth process timeout" 
      (let [!calls (atom [])
            user-socket (s/stream)
            user-id "pete"
            ret (do-conn-auth-aleph 150 250 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :timed-out))
        (is (= @!calls
               ["Authentification timed out! Disconnecting." "closed!"]))
        (is (s/closed? user-socket)))) 

    (testing "Login failed" 
      (let [!calls (atom [])
            user-socket (s/stream)
            user-id "non-user"
            ret (do-conn-auth-aleph 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :failed))
        (is (= @!calls
               ["Login failed! Disconnecting." "closed!"]))
        (is (s/closed? user-socket))))

    (testing "Login succeeded" 
      (let [!calls (atom [])
            user-socket (s/stream)
            user-id "pete"
            ret (do-conn-auth-aleph 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               true))
        (is (= (-> ret :auth-result)
               [:success "Login success!" user-id]))
        (is (= @!calls
               ["Login success!"]))
        (is (-> user-socket s/closed? not))))))
;; TODO: Copy-paste of connect-auth-immutant - abstract this?

;; TODO: 2 tests fail just on first run!
;; (run-tests)


