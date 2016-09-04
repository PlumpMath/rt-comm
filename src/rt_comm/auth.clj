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
                                  [:success (:user-id login)] 
                                  [:failed "User-id - password login failed! Disconnecting."])

         ["test"]               [:success "test-id"] 

         :timed-out             [:timed-out "Authentification timed out! Disconnecting."] 
         :else                  [:no-auth-cmd "Expected auth. command not found! Disconnecting."]))



