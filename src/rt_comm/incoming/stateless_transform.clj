(ns rt-comm.incoming.stateless-transform
  (:require  
    [clojure.core.async :as a] ;; for testing only ->
    [rt-comm.utils.async :as au]
    [manifold.stream :as s]
    [taoensso.timbre :refer [debug info error spy]]))


(defn map-assoc-user-id [user-id]
  "Returned fn takes a collection of maps and assocs ':user-id user-id' to each element."
  (->> #(assoc % :user-id user-id)
       (partial mapv)))


(defn filter-to-allowed-actns [allowed-actns]
  "Returned fn filters a collection of maps based on each element's :actn val being included in allowed-actns"
  (->> (comp (set allowed-actns) :actn) ;; predicate tests a map's :actn val to be one of allowed-actns. 
       (partial filterv))) 


;; TEST CODE:
;; (-> [{:actn :ada :idx 12} {:actn :aa :idx 13} {:idx 14}]
;;     ((filter-to-allowed-actns [:aa :bb]))
;;     ((map-assoc-user-id "pete")))


(defn incoming-tx [{:keys [user-id allowed-actns]}] 
  "Creates incoming transform transducer."
  (comp 
    (map (filter-to-allowed-actns allowed-actns)) ;; delete events with non-allowed actns in ev-coll msgs
    (filter seq)                                  ;; delete empty ev-coll msgs
    (map (map-assoc-user-id user-id))))           ;; assoc user-id to all events in ev-coll msgs 


;; TEST CODE:
;; (into [] 
;;       (incoming-tx {:user-id "paul"
;;                     :allowed-actns [:aa :bb]})
;;       [[{:actn :aa :idx 12}  ;; event-coll
;;         {:actn :ada :idx 13} ]
;;        [{:actn :dd :idx 14}  ;; event-coll
;;         {:idx 15}]
;;        [{:actn :bb :idx 20} ;; event-coll 
;;         {:actn :bb :idx 21} 
;;         {:idx 26}]])
;;
;; [({:actn :aa, :idx 12, :user-id "paul"}) 
;;  ({:actn :bb, :idx 20, :user-id "paul"} {:actn :bb, :idx 21, :user-id "paul"})]
;;
;; ;; MANIFOLD
;; (def s1 (s/stream))
;; (def in-tx (incoming-tx {:user-id "paul"
;;                          :allowed-actns [:aa :bb]}))
;; (def in-st (s/transform in-tx s1))
;;
;; (s/consume #(info (vec %)) in-st)
;;
;; (s/put! s1 [{:actn :aa :idx 12} 
;;             {:actn :aa :idx 13} 
;;             {:idx 14}] )
;; (s/put! s1 [{:actn :ada :idx 12} 
;;             {:idx 14}])
;;
;; ;; CORE.ASYNC
;; (def s1 (a/chan))
;; (def in-tx (incoming-tx {:user-id "paul"
;;                          :allowed-actns [:aa :bb]}))
;;
;; (def in-st (au/transform-ch s1 in-tx))
;;
;; (go (while true (info (<!! in-st))))
;;
;; (a/>!! s1 [{:actn :aa :idx 12} 
;;            {:actn :aa :idx 13} 
;;            {:idx 14}] )
;; (a/>!! s1 [{:actn :ada :idx 12} 
;;            {:idx 14}])












