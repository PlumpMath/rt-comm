(ns dev
  "Tools for interactive development with the REPL"
  (:require
    [clojure.java.io :as io]
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.test :as test]
    [clojure.tools.namespace.repl :as tn :refer [refresh]]

    [com.stuartsierra.component :as component]
    [datomic.api :as d]

    [nyse.utils.utils :as utils]
    [nyse.components.system :refer [new-dev-system]]
    [nyse.api :as api]
    [nyse.utils.datomic :refer [touch]]

))

(tn/set-refresh-dirs "src" "dev" "test")

(def dev-config-path "dev/resources/config.edn")

(def system nil)

(defn db-conns []
  {:datomic (-> system :datomic :conn)
   :dynamo  (-> system :dynamo  :creds)})

;; (:immutant system)

(defn init []
  (alter-var-root #'system ;; put a dev-system component here
                  (constantly (-> (utils/load-config dev-config-path)
                                  new-dev-system))))

(defn start []
  (alter-var-root #'system component/start)) ;; start the system by using the system component of the previous value?

(defn stop []
  (alter-var-root #'system 
    (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'dev/go))


;; (reset)





