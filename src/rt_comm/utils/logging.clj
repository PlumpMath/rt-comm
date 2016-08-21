(ns rt-comm.utils.logging
  (:require [clojure.java.io :as io] 
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as appenders]
            [json-html.core :as jh]
            [hiccup.page]
            ;; [taoensso.encore    :as enc]
            )
  (:use     [clojure.java.shell :only [sh]])
  )
;; 
(timbre/refer-timbre) ; set up timbre aliases

(def set-log-config-param! (partial timbre/swap-config! assoc-in))

(def log-file-name "log.txt")

;; Delete log from previous run
;; (io/delete-file log-file-name)

(identity timbre/*config*)

;; Config ------------------------------------------------------------------------

(defn main [] 
  ;; disable console print
  (set-log-config-param! [:appenders :println :enabled?] false)

  ;; enable file logging
  (set-log-config-param! [:appenders :spit] (appenders/spit-appender {:fname log-file-name}))

  ;; Set the lowest-level to output as :debug
  (timbre/set-level! :trace)

  ;; time pattern
  (set-log-config-param! [:timestamp-opts] {:pattern  "HH:mm:ss" #_:iso8601
                                            :locale   :jvm-default #_(java.util.Locale. "en")
                                            :timezone :utc         #_(java.util.TimeZone/getTimeZone "Europe/Amsterdam")})
  )
;; -------------------------------------------------------------------------------

(main)
;; (init)
;; (info "vier")

;; (+ 100 (spy (+ 3 4)))

(defn pretty-edn-html [v fname]
  (spit fname
        (hiccup.page/html5
         [:head [:style (-> "json.human.css" clojure.java.io/resource slurp)]]
         [:div {:style "max-width: 900px"} (jh/edn->html v)])))

(defn browse
  "Display an EDN-like value in the browser"
  [v]
  (let [fname (java.io.File/createTempFile "filename" ".html")]
    (pretty-edn-html v fname)
    (clojure.java.shell/sh "chrome.exe" (.getAbsolutePath fname))
    nil))


