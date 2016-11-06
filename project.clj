(defproject rt-comm "0.1.0"
  :description "Explore clojure real-time communication"
  :license {:name "MIT"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 #_[org.clojure/clojure "1.9.0-alpha12"]

                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [clojure-future-spec "1.9.0-alpha13"]

                 [org.clojure/core.async "0.2.374"]
                 [co.paralleluniverse/quasar-core "0.7.5"]
                 [co.paralleluniverse/pulsar "0.7.5"]

                 ;; logging/tools
                 [com.taoensso/timbre "4.3.1"]
                 [json-html "0.3.9"]
                 [hiccup "1.0.5"] 
                 [criterium "0.4.4"]

                 [org.immutant/immutant "2.1.3"]
                 [aleph "0.4.1"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [compojure "1.5.0"]

                 [cheshire "5.5.0"]
                 [ring-middleware-format "0.7.0"]

                 [metosin/ring-http-response "0.8.0"]
                 [ring/ring-defaults "0.2.0"]

                 [hiccups "0.3.0"]

                 ;; Client libs
                 [io.nervous/kvlt "0.1.3"]
                 [clj-http  "2.0.0"]

                 [com.datomic/datomic-free "0.9.5327" :exclusions [joda-time]]
                 [datascript "0.13.3"]
                 [io.nervous/hildebrand "0.4.3"]

                 ] 

  :java-agents  [[co.paralleluniverse/quasar-core "0.7.5"]]

  :plugins      [[lein-ancient "0.6.10"]
                 [lein-cljfmt "0.5.3"] 
                 [io.aviso/pretty "0.1.20"]]

  :repl-options {;; Default to 30000 (30 seconds)
                 :timeout 220000
                 }

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.11"]]
                   :source-paths ["dev"]
                   :jvm-opts [
                              ;; "-Dco.paralleluniverse.pulsar.instrument.auto=all"
                              ;; "-Dco.paralleluniverse.fibers.verifyInstrumentation=true"
                              ]}}
  )


