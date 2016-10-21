(ns rt-comm.incoming.connect-auth-test
  (:require [clojure.core.match :refer [match]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [clojure.core.async :as a]
            [co.paralleluniverse.pulsar.core :as p :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
            [co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                       register! unregister! self]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer [cond= valp]]
            [rt-comm.utils.logging :refer [browse]]

            [rt-comm.incoming.connect-auth :refer :all]))


(def user-data [{:user-id "pete" :pw "abc"} 
                {:user-id "paul" :pw "cde"} 
                {:user-id "mary" :pw "fgh"}])


(deftest auth
  (testing "auth-result" 
    (are [m res] (= (auth-result m user-data) res)
         {:cmd [:auth {:user-id "pete" :pw "abc"}]} [:success "Login success!" "pete"] 
         {:cmd [:auth {:user-id "pete" :pw "abd"}]} [:failed  "Login failed! Disconnecting."] 
         :timed-out [:timed-out "Authentification timed out! Disconnecting."]))

  (testing "auth-msg + auth-result" 
    (let [do-auth (fn [cmd wait]
                    (let [ch (p/channel)
                          fu (fiber (-> (auth-msg ch 20) 
                                        (auth-result user-data)))] ;; Start the auth process
                      (sleep wait)
                      (future (snd ch cmd)) ;; Send first/auth message 
                      @fu))]
      (is (= (do-auth {:cmd [:auth {:user-id "pete" :pw "abc"}]} 10) 
             [:success "Login success!" "pete"]))
      (is (= (do-auth {:cmd [:auth {:user-id "pete" :pw "abc"}]} 30) 
             [:timed-out "Authentification timed out! Disconnecting."])))))



(defn do-conn-auth [args snd-fn close-fn wait-conn wait-auth user-socket user-id !calls]
  "Connection and auth mimicking ws-handler code"
  (let [ws-user-args (merge args {:server-snd-fn   (fn [ch msg] (swap! !calls conj msg))
                                  :server-close-fn (fn [ch] (do (close-fn ch) 
                                                                (swap! !calls conj "closed!")))})  
        ;; TESTS THIS CODE:
        fib-rt (fiber (some-> ws-user-args 
                              (connect-process 200) ;; wait for connection and assoc user-socket
                              (cond= :server :aleph #(assoc % :ch-incoming (:user-socket %))) ;; with aleph the user-socket is also the ch-incoming
                              (assoc :user-data user-data)
                              ;; browse
                              (auth-process 200) ;; returns augmented init-ws-user-args or nil
                              ))]
    (sleep wait-conn)
    (deliver (:on-open-user-socket args) user-socket)
    (sleep wait-auth)
    (if (= (:server args) :aleph) 
      (future (snd-fn user-socket {:cmd [:auth {:user-id user-id :pw "abc"}]})) 
      (future (snd-fn (:ch-incoming args) {:cmd [:auth {:user-id user-id :pw "abc"}]})))
    @fib-rt))


(deftest connect-auth-manifold
  (testing "Connect and auth process manifold" 
    (testing "Connection process timeout" 
      (let [!calls (atom [])
            user-socket (s/stream)
            user-id "pete"
            args {:on-open-user-socket  (d/deferred)
                  :server :aleph}  
            ret (do-conn-auth args s/put! s/close! 350 50 user-socket user-id !calls)]
        (is (nil? ret) "yealds nil")
        (is (empty? @!calls) "sends no msgs to client")))

    (testing "Auth process timeout" 
      (let [!calls (atom [])
            user-socket (s/stream)
            user-id "pete"
            args {:on-open-user-socket  (d/deferred)
                  :server :aleph}  
            ret (do-conn-auth args s/put! s/close! 150 255 user-socket user-id !calls)]
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
            args {:on-open-user-socket  (d/deferred)
                  :server :aleph}  
            ret (do-conn-auth args s/put! s/close! 50 50 user-socket user-id !calls)]
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
            args {:on-open-user-socket  (d/deferred)
                  :server :aleph}  
            ret (do-conn-auth args s/put! s/close! 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               true))
        (is (= (-> ret :auth-result)
               [:success "Login success!" user-id]))
        (is (= @!calls
               ["Login success!"]))
        (is (-> user-socket s/closed? not))))))


(deftest connect-auth-pulsar
  (testing "Connect and auth process pulsar" 
    (testing "Connection process timeout" 
      (let [!calls (atom [])
            user-socket (p/channel)
            user-id "pete"
            args {:ch-incoming          (p/channel)
                  :on-open-user-socket  (p/promise)
                  :server :immutant}  
            ret (do-conn-auth args p/snd p/close! 350 50 user-socket user-id !calls)]
        (is (nil? ret) "yealds nil")
        (is (empty? @!calls) "sends no msgs to client")))

    (testing "Auth process timeout" 
      (let [!calls (atom [])
            user-socket (p/channel)
            user-id "pete"
            args {:ch-incoming          (p/channel)
                  :on-open-user-socket  (p/promise)
                  :server :immutant}  
            ret (do-conn-auth args p/snd p/close! 150 255 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :timed-out))
        (is (= @!calls
               ["Authentification timed out! Disconnecting." "closed!"]))
        (is (p/closed? user-socket))))


    (testing "Login failed" 
      (let [!calls (atom [])
            user-socket (p/channel)
            user-id "non-user"
            args {:ch-incoming          (p/channel)
                  :on-open-user-socket  (p/promise)
                  :server :immutant}  
            ret (do-conn-auth args p/snd p/close! 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :failed))
        (is (= @!calls
               ["Login failed! Disconnecting." "closed!"]))
        (is (p/closed? user-socket))))


    (testing "Login succeeded" 
      (let [!calls (atom [])
            user-socket (p/channel)
            user-id "pete"
            args {:ch-incoming          (p/channel)
                  :on-open-user-socket  (p/promise)
                  :server :immutant}  
            ret (do-conn-auth args p/snd p/close! 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               true))
        (is (= (-> ret :auth-result)
               [:success "Login success!" user-id]))
        (is (= @!calls
               ["Login success!"]))
        (is (-> user-socket p/closed? not))))))


(deftest connect-auth-coreasync
  (testing "Connect and auth process coreasync" 
    (testing "Connection process timeout" 
      (let [!calls (atom [])
            user-socket (a/chan)
            user-id "pete"
            args {:ch-incoming          (a/chan)
                  :on-open-user-socket  (promise)
                  :server :immutant}  
            ret (do-conn-auth args a/>!! a/close! 350 50 user-socket user-id !calls)]
        (is (nil? ret) "yealds nil")
        (is (empty? @!calls) "sends no msgs to client")))

    (testing "Auth process timeout" 
      (let [!calls (atom [])
            user-socket (a/chan)
            user-id "pete"
            args {:ch-incoming          (a/chan)
                  :on-open-user-socket  (promise)
                  :server :immutant}  
            ret (do-conn-auth args a/>!! a/close! 150 255 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :timed-out))
        (is (= @!calls
               ["Authentification timed out! Disconnecting." "closed!"]))))


    (testing "Login failed" 
      (let [!calls (atom [])
            user-socket (a/chan)
            user-id "non-user"
            args {:ch-incoming          (a/chan)
                  :on-open-user-socket  (promise)
                  :server :immutant}  
            ret (do-conn-auth args a/>!! a/close! 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               false))
        (is (= (-> ret :auth-result first)
               :failed))
        (is (= @!calls
               ["Login failed! Disconnecting." "closed!"]))))


    (testing "Login succeeded" 
      (let [!calls (atom [])
            user-socket (a/chan)
            user-id "pete"
            args {:ch-incoming          (a/chan)
                  :on-open-user-socket  (promise)
                  :server :immutant}  
            ret (do-conn-auth args a/>!! a/close! 150 150 user-socket user-id !calls)]
        (is (= (-> ret :auth-success)
               true))
        (is (= (-> ret :auth-result)
               [:success "Login success!" user-id]))
        (is (= @!calls
               ["Login success!"]))))))

;; TODO: 2 tests fail just on first run!
;; (run-tests)


