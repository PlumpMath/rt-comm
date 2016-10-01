(ns rt-comm.incoming-ws-user-coreasync-test
  (:require [clojure.core.match :refer [match]]

            [clojure.test :as t :refer [is are run-tests deftest testing]]
            [taoensso.timbre :refer [debug info error spy]]

            [rt-comm.utils.utils :refer [valp]]

            ))

#_(deftest auth
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

