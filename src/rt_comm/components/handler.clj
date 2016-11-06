(ns rt-comm.components.handler
  (:require
    [com.stuartsierra.component :as component]
    [compojure.core :as composure :refer [routes GET POST]]

    [rt-comm.middleware :as mw]

    [taoensso.timbre :refer [debug info error spy]]

    ;; [ring.util.response :as util]
    [ring.util.http-response :as resp]
    ;; [ring.middleware.defaults :refer [site-defaults]]
    ;; [ring.middleware.format :refer [wrap-restful-format]]
    [datomic.api :as d]    
    [cheshire.core :as json]

    [rt-comm.api :as api]
    [rt-comm.utils.utils :as u :refer [valp]]

    ))

(defn- make-handler [db-conns ws-handler-simple ws-handler-main]

  (composure/routes

    (GET "/ws-simple" request (ws-handler-simple request))

    (GET "/ws-main"   request (ws-handler-main request))

    (GET "/rtc/ab/:cc" [cc] (resp/ok {:ab 234 
                                      :cd [22 44]
                                      :we {:a 2 :bb cc}}))

    (GET "/rtc/bb/" [aa cc] (resp/ok {:cd [22 44]
                                      :we {:a aa :bb cc}}))


    (GET "/rtc/bbb/" req (resp/ok (do (info "bbb:" (u/pprint-s req))
                                      {:cd [22 44]
                                       :we {:a (-> req :params :aa) :bb (-> req :params :cc)}})))

    (GET "/rtc/bbbb/" req (resp/ok (do (info "bbb:" (u/pprint-s req))
                                       {:cd [22 44]
                                        :we 14})))

    (POST "/rtc/aabb/" [aa cc :as req] (do (info "aabb request:" (u/pprint-s req)) 
                                           (resp/ok {:aa aa
                                                     :bb cc})))

    (POST "/rtc/ab/" req (resp/ok {:ab 234 
                                   :cd [22 44]
                                   :we {:a 2 :bb (-> req :params :ee)}}))

    (POST "/rtc/abf/" req (do (info (u/pprint-s req)) 
                              (resp/ok {:aa 432
                                        :eeg (-> req :params :eeg)})))

    (POST "/rtc/abg/" [eeg] (resp/ok {:ab 234 
                                      :cd [22 44]
                                      :we {:a 2 :bb eeg}}))


    (GET "/rtc/orders/:ticker" [ticker]
         (-> (update db-conns :datomic d/db) ;; db snapshot 
             (api/find-orders ticker)
             resp/ok))

    (GET "/rtc/orders" []
         (-> (update db-conns :datomic d/db) ;; db snapshot
             api/find-all-orders
             resp/ok))


    (POST "/rtc/orders" [ticker qty bid offer]
          (let [order {:ticker ticker
                       :bid    (bigdec bid)
                       :offer  (bigdec offer)
                       :qty    (Integer/parseInt qty)}]

            (api/add-order! db-conns order)
            ;; API-FN + mutable dep + new data
            ;; TODO if error is returned - return to client

            (resp/created ticker {:added order})))))

;; (do (info "gott:") "heard you!")
(defrecord Handler [datomic dynamo ws-handler-simple ws-handler-main handler]
  component/Lifecycle

  (start [component]
    (assoc component :handler (-> (make-handler {:datomic (:conn datomic)
                                                 :dynamo  (:creds dynamo)}
                                                (:ws-handler ws-handler-simple)
                                                (:ws-handler ws-handler-main)) 
                                  mw/wrap-api-base)))

  (stop [component]
    (assoc component :handler nil)))




