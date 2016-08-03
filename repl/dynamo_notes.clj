(require
  '[dev]
  '[nyse.utils.dynamo :as ut]
  '[hildebrand.core :as hc]
  '[hildebrand.channeled :as hch]
  '[clojure.tools.namespace.repl :as tn]
  '[clojure.core.async :as a :refer [chan <! >! go-loop go alt!! 
                                     <!! >!!
                                     close! put! take! thread timeout
                                     offer! poll! promise-chan
                                     sliding-buffer]]    
  )

(tn/disable-reload!)
;;
(def creds (-> dev/system :dynamo :creds))
;;
;; (def creds
;;   {:region     "localhost"
;;    :endpoint   "http://localhost:8000"
;;    :access-key "accessk"
;;    :secret-key "seck"})

(defn delete-all-tables!! []
  (doseq [t-name (hc/list-tables!! (-> dev/system :dynamo :creds))] 
    (hc/delete-table!! creds t-name)))

(delete-all-tables!!)

(ut/init-tables! (-> dev/system :dynamo :creds) 
                 (-> dev/system :dynamo :conf :table-specs))

;; [{:name :orders, :spec-path "resources/dynamo-table-spec-orders.edn"}]
;;
(ut/load-table-spec (-> dev/system :dynamo :conf :table-specs first :spec-path))
;; ==>
{:table         :orders,
 :throughput    {:read 1, :write 1},
 :attrs         {:id :number, :symbol :string},
 :keys          [:id],
 :indexes
 {:global
  [{:name       :symbol,
    :keys       [:symbol],
    :project    [:all],
    :throughput {:read 1, :write 1}}]}}



;; ;; ==>
;;
;; ;; (<!! (hc/ensure-table! 


(hc/list-tables!! (-> dev/system :dynamo :creds))

(hc/get-item!! creds :orders {:symbol "appl"})

;;
;; [:curriesi :orders :orders2 :orders3 :orders4 :ordersh4]
;;
;;

(hc/create-table!!
  creds
  {:table      :orders
   :throughput {:read 1 :write 1}

   :attrs      {:id     :number
                :symbol :string
                ;; :bid    :number
                ;; :offer  :number
                ;; :qty    :number
                }

   :keys       [:id]

   :indexes {:global
             [{:name :symbol
               :keys [:symbol]
               :project [:all]
               :throughput {:read 1 :write 1}}]}
   })

#_(hc/create-table!!
 creds
 {:table :curries
  :throughput {:read 1 :write 1}
  :attrs {:name :string :region :string :spiciness :number}
  :keys  [:name]
  :indexes {:global
            [{:name :curries-by-region-spiciness
              :keys [:region :spiciness]
              :project [:all]
              :throughput {:read 1 :write 1}}]}
  })

#_(hc/create-table!!
 creds
 {:table :curries1
  :throughput {:read 1 :write 1}
  :attrs {:name :string :region :string :spiciness :number}
  :keys  [:name]})

#_(hc/create-table!!
 creds
 {:table :curries22
  :throughput {:read 1 :write 1}
  :attrs {:name :string}
  :keys  [:name]})

#_(hc/create-table!!
  creds
  {:table :curries3
   :throughput {:read 1 :write 1}
   :attrs {:name :string :region :string}
   :keys  [:name]
   :indexes {:global
             [{:name :test3
               :keys [:region]
               :project [:all]
               :throughput {:read 1 :write 1}}]}
   })

#_(hc/create-table!!
 creds
 {:table :curries4
  :throughput {:read 1 :write 1}
  :attrs {:name :string :region :string}
  :keys  [:name :region]})

#_(hc/create-table!!
 creds
 {:table :curries5
  :throughput {:read 1 :write 1}
  :attrs {:name :string :region :string :spiciness :number}
  :keys  [:name :region :spiciness]})



#_(hc/create-table!!
  (-> dev/system :dynamo :creds) 
  {:table      :orders6,
   :throughput {:read 1, :write 1},
   :attrs      {:one :string :two :number :three :number},
   :keys       [:one :two :three]})

#_(hc/create-table!!
 creds
 {:table :curries7
  :throughput {:read 1 :write 1}
  :attrs {:name :string :region :string :spiciness :number}
  :keys  [:name]
  :indexes {:global
            [{:name :curries-by-region-spiciness
              :keys [:region ]
              :project [:all]
              :throughput {:read 1 :write 1}}]}
  })


#_{:table      :orders
 :throughput {:read 1 :write 1}

 :attrs      {:symbol :string
              :bid    :number
              :offer  :number
              :qty    :number}

 :keys       [:symbol]

 :indexes {:global
           [{:name :curries-by-region-spiciness
             :keys [:region :spiciness]
             :project [:all]
             :throughput {:read 1 :write 1}}]}

 }
;;
;;
;;
;; {:table      :orders,
;;  :throughput {:read 1, :write 1},
;;  :attrs      {:symbol :string, :bid :number, :offer :number, :qty :number},
;;  :keys       [:symbol]}



;; (hc/create-table!
;;        creds
;;        {:table      :orders
;;         :throughput {:read 1 :write 1}
;;
;;         :attrs      {:name      :string
;;                      :region    :string
;;                      :spiciness :number}
;;
;;         :keys       [:name]
;;         :indexes    {:global
;;                      [{:name :curries-by-region-spiciness
;;                        :keys [:region :spiciness]
;;                        :project [:all]
;;                        :throughput {:read 1 :write 1}}]}})
;;
;; (<!! (hc/create-table!
;;        creds
;;        {:table      :curries
;;         :throughput {:read 1 :write 1}
;;
;;         :attrs      {:name      :string
;;                      :region    :string
;;                      :spiciness :number}
;;
;;         :keys       [:name]
;;         :indexes    {:global
;;                      [{:name :curries-by-region-spiciness
;;                        :keys [:region :spiciness]
;;                        :project [:all]
;;                        :throughput {:read 1 :write 1}}]}}))
;;
;; (<!! (hc/put-item!
;;        creds
;;        :curries
;;        {:name        "eins2"
;;         :region      "Pakistan"
;;         :spiciness   6
;;         :allergens   #{"clove" "apple"}
;;         :ingredients {"onion" 2 "tomato" 3 "chili" 2}}))

(<!! (hc/put-item!
       creds
       :orders
       {:symbol        "eins4"
        :bid      44
        :qty      6
        :offer    234
        }))

(hc/describe-table!! creds :orders)

;;
;;
;;
;; (hc/get-item!! creds :curries {:name "eins"})
;; ;; ==>
;; {:name        "eins",
;;  :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;  :spiciness   6N,
;;  :region      "Pakistan",
;;  :allergens   #{:clove :cinnamon}}
;;
;;
;; (hc/query!!
;;  creds
;;  :curries
;;  {:region [:= "Pakistan"] :spiciness [:< 7]}
;;  {:index :curries-by-region-spiciness
;;   :limit 10})
;; ;; ==>
;; ({:name        "eins",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :cinnamon}}
;;  {:name        "eins2",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :cinnamon}})
;;
;;
;; (hc/query!!
;;   creds
;;   :curries
;;   {:region [:= "Pakistan"] :spiciness [:< 7]}
;;   {:index :curries-by-region-spiciness
;;    :limit 10
;;    :filter [:contains [:allergens] "apple"]
;;    #_[:and [:exists [:ingredients :onion]]
;;             [:not [:contains [:allergens] "cinnamon"]]]
;;    }
;;   )
;; ;; ==>
;; ({:name        "eins2",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :apple}})
;; ;; ==>
;; ({:name        "eins",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :cinnamon}})
;; ;; ==>
;; ({:name        "eins2",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :apple}})
;;
;;
;; (hildebrand/update-item!
;;  creds
;;  :curries
;;  {:name "Jalfrezi"}
;;  {:ingredients {:onion [:inc 4] :tomato [:remove]}
;;   :allergens   [:concat #{"mustard" "nuts"}]
;;   :delicious   [:init true]}
;;  {:when [:and
;;           [:< [:ingredients :onion] [:ingredients :tomato]]
;;           [:contains [:allergens] "clove"]]})
;;
;;
;; (<!! (a/into [] (hch/list-tables! creds {:limit 20})))
;; (hc/list-tables!! creds {:limit 20})
;;
;; (hc/scan!! creds :curries {:limit 20})
(hc/scan!! creds :orders {:limit 20})
;; ==>
({:offer 234N, :symbol "eins2", :bid 44N, :qty 6N})

;; ;; ==>
;; ({:name        "eins",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :cinnamon}}
;;  {:name        "eins2",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   6N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :apple}})
;;
;; (hc/query!! creds :curries {:name [:= "eins"]} {:limit 20}) 
;; ;; The 'where' map needs an equality key


;; ({:name "Jalfrezi", :ingredients {:onion 2N, :chili 2N, :tomato 3N}, :spiciness 4N, :region "Pakistan", :allergens #{:clove :cinnamon}}
;;  {:name "eins",     :ingredients {:onion 2N, :chili 2N, :tomato 3N}, :spiciness 6N, :region "Pakistan", :allergens #{:clove :cinnamon}})
;;
;;
;; ({:name        "Jalfrezi",
;;   :ingredients {:onion 2N, :chili 2N, :tomato 3N},
;;   :spiciness   4N,
;;   :region      "Pakistan",
;;   :allergens   #{:clove :cinnamon}})
;;
;;
;;

