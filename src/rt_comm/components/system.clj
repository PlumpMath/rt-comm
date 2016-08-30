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
    [rt-comm.components.ws-handler-immutant-simple :refer [->Ws-Handler-Immutant-simple]]
    [rt-comm.components.ws-handler-aleph-simple    :refer [->Ws-Handler-Aleph-simple]]

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
    :handler-immutant (->Handler nil nil nil nil nil) ; datomic, dynamo, ws-handler-simple, ws-handler-main, + handler
    :handler-aleph    (->Handler nil nil nil nil nil) ; datomic, dynamo, ws-handler-simple, ws-handler-main, + handler

    ;; Websocket connections
    :ws-handler-immutant-simple (->Ws-Handler-Immutant-simple nil nil) ;; clients, + handler
    :ws-handler-aleph-simple    (->Ws-Handler-Aleph-simple    nil nil) ;; clients, + handler

    :ws-clients-simple   (atom #{}) 
    ;; Callbacks that put to the ws-socket connections (eigther Immutant or Aleph)

    :ws-clients-main     (atom [])

    )
  )


(def dependancy-map
  {
   :ws-handler-immutant-simple {:ws-clients :ws-clients-simple}
   :ws-handler-aleph-simple    {:ws-clients :ws-clients-simple} 

   :handler-immutant  {:datomic           :datomic
                       :dynamo            :dynamo
                       :ws-handler-simple :ws-handler-immutant-simple
                       :ws-handler-main   :ws-handler-immutant-simple
                       }

   :handler-aleph     {:datomic           :datomic
                       :dynamo            :dynamo
                       :ws-handler-simple :ws-handler-aleph-simple
                       :ws-handler-main   :ws-handler-aleph-simple
                       }

   :immutant    {:handler :handler-immutant}
   :aleph       {:handler :handler-aleph}
   })


(defn new-dev-system [conf]
  (-> (system-map conf) 
      (component/system-using dependancy-map)))




