(ns rt-comm.components.handler-notes
  (:require
    [com.stuartsierra.component :as component]
    [compojure.core :as composure :refer [routes GET POST]]
    ;; [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
    ;; [ring.middleware.params :as params]

    [rt-comm.middleware :as mw]

    [taoensso.timbre :refer [debug info error spy]]


    ;; [ring.util.response :as util]
    ;; [ring.middleware.defaults :refer [site-defaults]]
    ;; [ring.middleware.format :refer [wrap-restful-format]]
    [datomic.api :as d]    
    [cheshire.core :as json]

    [rt-comm.utils.utils :as u :refer [valp]]

    ))

(defn- make-handler []

  (composure/routes

    (GET "/rtc/ab/:cc" [cc] (str {:ab 234 
                                  :cd [22 "this is jetty 2"]
                                  :we {:a 2 :bb cc}}))

    (POST "/rtc/ab/" req (str {:ab 234 
                               :cd [22 44]
                               :we {:a 2 :bb (-> req :params :ee)}}))

    (POST "/rtc/abf/" req (do #_(info (str "hii!!" (-> req :params :eeg))) 
                              (info (u/pprint-s req)) 
                              ;; (info (-> req :body)) 
                              (str "hii!!" (-> req :params :eeg))))


    (POST "/rtc/abg/" [eeg] (str {:ab 234 
                                 :cd [22 44]
                                 :we {:a 2 :bb eeg}}))


    (POST "rtc/tee" [req] (do (info "gott:" req) "heard you!"))

    (GET "rtc/tee" [] "hiii")


    ))


(defrecord Handler-notes [handler]
  component/Lifecycle

  (start [component]
    (assoc component :handler (-> (make-handler)
                                  mw/wrap-formats))
    )

  (stop [component]
    (assoc component :handler nil)))




