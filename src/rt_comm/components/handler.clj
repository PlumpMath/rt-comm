(ns rt-comm.components.handler
  (:require
    [com.stuartsierra.component :as component]
    [compojure.core :as composure :refer [routes GET POST]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]

    [taoensso.timbre :refer [debug info error spy]]

    [ring.middleware.params :as params]

    ;; [ring.util.response :as util]
    ;; [ring.middleware.defaults :refer [site-defaults]]
    ;; [ring.middleware.format :refer [wrap-restful-format]]
    [datomic.api :as d]    
    [cheshire.core :as json]

    [rt-comm.api :as api]

    ))

(defn- make-handler [db-conns ws-handler]

  (composure/routes

    (GET "/ws" request (ws-handler request))

    (GET "/rtc/ab/:cc" [cc] (str {:ab 234 
                                  :cd [22 44]
                                  :we {:a 2 :bb cc}}))

    (GET "/rtc/orders/:ticker" [ticker]
           (-> (update db-conns :datomic d/db) ;; db snapshot 
               (api/find-orders ticker)
               json/generate-string))

    (GET "/rtc/orders" []
         (-> (update db-conns :datomic d/db) ;; db snapshot
             api/find-all-orders
             json/generate-string))

    (POST "/rtc/orders" [ticker qty bid offer]
          (let [order {:ticker ticker
                       :bid    (bigdec bid)
                       :offer  (bigdec offer)
                       :qty    (Integer/parseInt qty)}]

            (api/add-order! db-conns order)
            ;; API-FN + mutable dep + new data
            ;; TODO if error is returned - return to client

            (json/generate-string {:added order})))))


(defrecord Handler [datomic dynamo ws-handler handler]
  component/Lifecycle

  (start [component]
    (assoc component :handler (-> (make-handler {:datomic (:conn datomic)
                                                 :dynamo  (:creds dynamo)}
                                                (:handler ws-handler)) 
                                  (wrap-defaults api-defaults)))
    )

  (stop [component]
    (assoc component :handler nil)))




