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
    [rt-comm.components.jetty       :refer [->Jetty]]

    ;; Http Handler/ Routes
    [rt-comm.components.handler       :refer [->Handler]]
    [rt-comm.components.handler-notes :refer [->Handler-notes]]

    ;; Websocket handler: Simple example
    [rt-comm.components.ws-handler-immutant-simple :refer [->Ws-Handler-Immutant-simple]]
    [rt-comm.components.ws-handler-aleph-simple    :refer [->Ws-Handler-Aleph-simple]]
    ;; Websocket handler: Main example
    [rt-comm.components.ws-handler-immutant-main   :refer [->Ws-Handler-Immutant-main]]
    [rt-comm.components.ws-handler-aleph-main      :refer [->Ws-Handler-Aleph-main]]

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
    :jetty      (->Jetty    (:jetty    conf) nil nil)  ;; -> conf, handler, + server

    ;; Http Handler/ Routes
    :handler-immutant (->Handler nil nil nil nil nil) ; datomic, dynamo, ws-handler-simple, ws-handler-main, + handler
    :handler-aleph    (->Handler nil nil nil nil nil) ; datomic, dynamo, ws-handler-simple, ws-handler-main, + handler
    :handler-jetty    (->Handler-notes nil) ; + handler

    ;; Websocket handler: Simple example
    :ws-handler-immutant-simple (->Ws-Handler-Immutant-simple nil nil) ;; clients, + handler
    :ws-handler-aleph-simple    (->Ws-Handler-Aleph-simple    nil nil) ;; clients, + handler
    ;; Websocket handler: Main example
    :ws-handler-immutant-main   (->Ws-Handler-Immutant-main (:ws-user conf) nil nil nil) ;; conf, ws-conns-main, event-queue, + handler
    :ws-handler-aleph-main      (->Ws-Handler-Aleph-main    (:ws-user conf) nil nil nil) ;; conf, ws-conns-main, event-queue, + handler

    :ws-conns-simple   (atom []) 
    ;; Callbacks that put to the ws-socket connections (eigther Immutant or Aleph)
    ;; [{:socket req-client-socket :cb send-to-this-client-cb} {:socket .. :cb ..}] 
    ;; (def user-conn (-> system :ws-conns-simple deref first))

    :ws-conns-main     (atom [])

    )
  )


(def dependancy-map
  {
   ;; Websocket handler: Simple example
   :ws-handler-immutant-simple {:ws-conns :ws-conns-simple}
   :ws-handler-aleph-simple    {:ws-conns :ws-conns-simple} 
   ;; Websocket handler: Main example
   :ws-handler-immutant-main   {:ws-conns :ws-conns-main :event-queue :event-queue}
   :ws-handler-aleph-main      {:ws-conns :ws-conns-main :event-queue :event-queue} 

   ;; Http Handler/ Routes
   :handler-immutant  {:datomic           :datomic
                       :dynamo            :dynamo
                       :ws-handler-simple :ws-handler-immutant-simple
                       :ws-handler-main   :ws-handler-immutant-main}

   :handler-aleph     {:datomic           :datomic
                       :dynamo            :dynamo
                       :ws-handler-simple :ws-handler-aleph-simple
                       :ws-handler-main   :ws-handler-aleph-main}

   ;; Server
   :immutant    {:handler :handler-immutant}
   :aleph       {:handler :handler-aleph}
   :jetty       {:handler :handler-jetty}
   })


(defn new-dev-system [conf]
  (-> (system-map conf) 
      (component/system-using dependancy-map)))




