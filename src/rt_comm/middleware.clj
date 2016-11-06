(ns rt-comm.middleware
  (:require [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            ;; [ring.middleware.params :as params]
            ))


;; Taken from Luminus
(defn wrap-formats [handler]
  (let [wrapped (wrap-restful-format
                  handler
                  {:formats [:json-kw :transit-json :edn :yaml-kw]})]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))


(defn wrap-api-base [handler]
  (-> handler
      (wrap-defaults api-defaults)
      wrap-formats
      ))


