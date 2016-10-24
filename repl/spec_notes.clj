(require 
  '[dev :refer [system db-conns reset]]
  '[rt-comm.api :refer [add-order! find-orders find-all-orders]]
  '[rt-comm.utils.logging :as logging]
  '[rt-comm.utils.utils :as utils :refer [valp fpred add-to-col-in-table]] 
  '[rt-comm.utils.async :refer [rcv-rest pause-filter-keys]]
  '[clojure.core.match :refer [match]]
  '[clojure.spec :as sp] 
  '[clojure.future :refer :all] 

  '[co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]
  '[co.paralleluniverse.pulsar.async :as pa]
  '[co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                              register! unregister! self]]

  '[aleph.http :as http]
  '[manifold.stream :as st]
  '[manifold.deferred :as d]
  '[manifold.time :as t]
  '[manifold.bus :as bus]

  '[taoensso.timbre :refer [debug info error spy]]
  '[clojure.tools.namespace.repl :as tn]
  '[clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                     <!! >!!
                                     close! mult tap untap put! take! thread timeout
                                     offer! poll! promise-chan
                                     sliding-buffer]])


(def suit? #{:club :diamond :heart :spade})
(def rank? (into #{:jack :queen :king :ace} (range 2 11)))

(def deck (for [suit suit? rank rank?] [rank suit]))

(sp/def ::card (sp/tuple rank? suit?))
(sp/def ::hand (sp/* ::card))

(sp/def ::name string?)
(sp/def ::score int?)
(sp/def ::player (sp/keys :req [::name ::score ::hand]))

(sp/def ::players (sp/* ::player))
(sp/def ::deck (sp/* ::card))
(sp/def ::game (sp/keys :req [::players ::deck]))

(def kenny
  {::name "Kenny Rogers"
   ::score 100
   ::hand []})

(sp/valid? ::player kenny)

(sp/explain ::game
  {::deck deck
   ::players [{::name "Kenny Rogers"
               ::score 100
               ::hand [[2 :banana]]}]})


(defn total-cards [{:keys [::deck ::players] :as game}]
  (apply + (count deck)
    (map #(-> % ::hand count) players)))

(defn deal [game] ,,,)

(sp/fdef deal
  :args (sp/cat :game ::game)
  :ret ::game
  :fn #(= (total-cards (-> % :args :game))
          (total-cards (-> % :ret))))


;; -------------------------------------------------------------------------------

(def fish-numbers {0 "Zero"
                   1 "One"
                   2 "Two"})

(sp/def ::fish-number (set (keys fish-numbers)))

(sp/valid? ::fish-number 3)
(sp/explain ::fish-number 3)

(sp/def ::color #{"Red" "Blue" "Dun"})

(sp/def ::first-line (sp/cat :n1 ::fish-number 
                             :n2 ::fish-number 
                             :c1 ::color 
                             :c2 ::color))

(sp/explain ::first-line [0 1 "Red" "Blue"])
(sp/conform ::first-line [0 1 "Red" "Blue"])


(defn one-bigger? [{:keys [n1 n2]}]
  (= n2 (inc n1)))

(one-bigger? [3 4 :aa])

(sp/def ::first-line (sp/and (sp/cat :n1 ::fish-number :n2 ::fish-number :c1 ::color :c2 ::color)
                             one-bigger?
                             #(not= (:c1 %) (:c2 %))))

(s/fdef fish-line
        :args ::first-line
        :ret  string?)

(defn fish-line [n1 n2 c1 c2]
  (clojure.string/join " "
    (map #(str % " fish.")
      [(get fish-numbers n1)
       (get fish-numbers n2)
       c1
       c2])))

(fish-line 1 2 "Red" "Blue")

