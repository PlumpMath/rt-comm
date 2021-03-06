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
    [rt-comm.components.websockets-immutant :refer [->Ws-Handler-Immutant]]
    [rt-comm.components.websockets-aleph    :refer [->Ws-Handler-Aleph]]

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
    :ws-handler-immutant (->Ws-Handler-Immutant nil nil) ;; clients, + handler
    :ws-handler-aleph    (->Ws-Handler-Aleph    nil nil) ;; clients, + handler

    :clients    (atom #{}) 
    ;; Callbacks that put to the ws-socket connections (eigther Immutant or Aleph)


    )
  )


(def dependancy-map
  {
   :ws-handler-immutant [:clients]
   :ws-handler-aleph    [:clients]

   :handler-immutant  {:datomic    :datomic
                       :dynamo     :dynamo
                       :ws-handler :ws-handler-immutant
                       }

   :handler-aleph     {:datomic    :datomic
                       :dynamo     :dynamo
                       :ws-handler :ws-handler-aleph
                       }

   :immutant    {:handler :handler-immutant}
   :aleph       {:handler :handler-aleph}
   })


(defn new-dev-system [conf]
  (-> (system-map conf) 
      (component/system-using dependancy-map)))




