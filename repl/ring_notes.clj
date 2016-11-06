(require 
  '[dev :refer [system db-conns reset]]
  '[rt-comm.api :refer [add-order! find-orders find-all-orders]]
  '[rt-comm.utils.logging :as logging]
  '[rt-comm.utils.utils :as u :refer [valp fpred add-to-col-in-table]] 
  '[rt-comm.utils.async :refer [rcv-rest pause-filter-keys]]
  '[clojure.core.match :refer [match]]
  '[clojure.spec :as sp] 
  '[clojure.future :refer :all] 

  '[co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]
  '[co.paralleluniverse.pulsar.async :as pa]
  '[co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                              register! unregister! self]]

  '[aleph.http :as http]
  '[ring.adapter.jetty :as jetty]
  '[compojure.core :as composure :refer [routes GET POST]]


  '[manifold.stream :as st]
  '[manifold.deferred :as d]
  '[manifold.time :as t]
  '[manifold.bus :as bus]

  '[taoensso.timbre :refer [debug info error spy]]
  '[clojure.tools.namespace.repl :as tn]
  '[clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                     <!! >!!
                                     close! mult tap untap put! take! thread timeout
                                     offer! poll! promise-chan
                                     sliding-buffer]])

(defn random-uuid []
  (java.util.UUID/randomUUID))

(def contacts (atom (let [id (random-uuid)]
                      {id {:id id
                           :full-name "turtle"
                           :skills ["LISP" "Lambda"]}})))


(defn handler 
  "request -> response" 
  [req] 
  (if (and (= (:request-method req) :get)
           (= (:uri req) "/"))
    ;; (info "got:" (u/pprint-s req))
    (do (info "got:" (:uri req))
        {:status 200
         :headers {"Content-Type" "application/edn; charset=UTF-8"}
         :body (prn-str (vals @contacts))}))
  )


(defn handler 
  "request -> response" 
  [req] 
  {:status 200
   :headers {"Content-Type" "text/plain; charset=UTF-8"}
   :body "some simple text 2"}
  )

(defonce server 
  (jetty/run-jetty #'handler {:port 3000
                              :join? false}))

;; (.stop server)


;; Run the first handler that does not return nil!
(defn application-routes [req]
  (some #(% req) [home-page-handler
                  about-page-handler]))

(defn application-routes [req]
  (compojure/routes home-page-handler
                    about-page-handler) req)

(compojure/defroutes app-routes
  home-page-handler
  about-page-handler)




