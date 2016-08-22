(ns rt-comm.components.system
  (:require
    [com.stuartsierra.component :as component]

    ;; DBs
    [rt-comm.components.datomic     :refer [->Datomic]]
    [rt-comm.components.dynamo      :refer [->Dynamo]]

    [rt-comm.components.event-queue :refer [->EventQueue]]

    ;; Webserver
    [rt-comm.components.immutant    :refer [->Immutant]]
    [rt-comm.components.aleph       :refer [->Aleph]]

    ;; Routes/ Handler
    [rt-comm.components.handler     :refer [->Handler]]

    ;; Websocket connections
    ;; [rt-comm.components.websockets  :refer [->Ws-Handler]]
    [rt-comm.components.websockets-immutant :refer [->Ws-Handler-Immutant]]
    ;; [rt-comm.components.websockets-aleph    :refer [->Ws-Handler-Aleph]]

))


(defn system-map [conf]
  (component/system-map
    ;; DBs
    :datomic     (->Datomic (:datomic conf) nil) ;; conf, + conn
    :dynamo      (->Dynamo  (:dynamo  conf) nil) ;; conf, + creds

    :event-queue (->EventQueue (:event-queue conf) nil) ;; conf, + events-server

    ;; Webserver
    :immutant   (->Immutant (:immutant conf) nil nil)  ;; -> conf, handler, + server
    :aleph      (->Aleph    (:aleph    conf) nil nil)  ;; -> conf, handler, + server

    ;; Routes/ Handler
    :handler-immutant (->Handler nil nil nil nil) ; datomic, dynamo, ws-handler, + handler
    :handler-aleph    (->Handler nil nil nil nil) ; datomic, dynamo, ws-handler, + handler

    ;; Websocket connections
    ;; :ws-handler (->Ws-Handler nil nil) ;; clients, + handler
    :ws-handler-immutant (->Ws-Handler-Immutant nil nil) ;; clients, + handler
    ;; :ws-handler-aleph    (->Ws-Handler-Aleph    nil nil) ;; clients, + handler

    :clients    (atom #{}) 
    ;; keep this within ws-handler? - but it's shared between two ws-handlers!


    )
  )


(def dependancy-map
  {
   ;; :ws-handler  [:clients]
   :ws-handler-immutant [:clients]
   ;; :ws-handler-aleph    [:clients]

   ;; :handler1    [:datomic :dynamo :ws-handler]
   :handler-immutant    [:datomic :dynamo :ws-handler-immutant]
   ;; :handler2    [:datomic :dynamo :ws-handler-aleph]

   :immutant    [:handler-immutant]
   ;; :aleph       [:handler2]
   })


(defn new-dev-system [conf]
  (-> (system-map conf) 
      (component/system-using dependancy-map)))




