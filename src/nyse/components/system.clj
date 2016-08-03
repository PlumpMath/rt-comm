(ns nyse.components.system
  (:require
    [com.stuartsierra.component :as component]

    ;; DBs
    [nyse.components.datomic     :refer [->Datomic]]
    [nyse.components.dynamo      :refer [->Dynamo]]

    ;; [nyse.components.event-queue :refer [->EventQueue]]

    ;; Webserver
    [nyse.components.immutant    :refer [->Immutant]]
    ;; [nyse.components.aleph      :refer [->Aleph]]

    ;; Routes/ Handler
    [nyse.components.handler     :refer [->Handler]]

    ;; Websocket connections
    [nyse.components.websockets  :refer [->Ws-Handler]]
    ;; [nyse.components.websockets-immutant :refer [->Ws-Handler-Immutant]]
    ;; [nyse.components.websockets-aleph    :refer [->Ws-Handler-Aleph]]

))


(defn system-map [conf]
  (component/system-map
    ;; DBs
    :datomic     (->Datomic (:datomic conf) nil) ;; conf, + conn
    :dynamo      (->Dynamo  (:dynamo  conf) nil) ;; conf, + creds

    ;; :event-queue (->EventQueue (:event-queue conf) nil) ;; conf, + events-server

    ;; Webserver
    :immutant   (->Immutant (:immutant conf) nil nil)  ;; -> conf, handler, + server
    ;; :aleph      (->Aleph    (:aleph    conf) nil nil)  ;; -> conf, handler, + server

    ;; Routes/ Handler
    :handler1   (->Handler nil nil nil nil) ; datomic, dynamo, ws-handler, + handler
    ;; :handler2   (->Handler nil nil nil nil) ; datomic, dynamo, ws-handler, + handler

    ;; Websocket connections
    :ws-handler (->Ws-Handler nil nil) ;; clients, + handler
    ;; :ws-handler-immutant (->Ws-Handler-Immutant nil nil) ;; clients, + handler
    ;; :ws-handler-aleph    (->Ws-Handler-Aleph    nil nil) ;; clients, + handler

    :clients    (atom #{}) 
    ;; keep this within ws-handler? - but it's shared between two ws-handlers!


    )
  )


(def dependancy-map
  {
   :ws-handler  [:clients]
   ;; :ws-handler-immutant [:clients]
   ;; :ws-handler-aleph    [:clients]

   :handler1    [:datomic :dynamo :ws-handler]
   ;; :handler1    [:datomic :dynamo :ws-handler-immutant]
   ;; :handler2    [:datomic :dynamo :ws-handler-aleph]

   :immutant    [:handler1]
   ;; :aleph       [:handler2]
   })


(defn new-dev-system [conf]
  (-> (system-map conf) 
      (component/system-using dependancy-map)))




