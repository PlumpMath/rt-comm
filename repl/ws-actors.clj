(require 
  '[dev :refer [system db-conns reset]]
  '[rt-comm.api :refer [add-order! find-orders find-all-orders]]
  '[rt-comm.utils.logging :as logging]

  '[clojure.core.match :refer [match]]

  '[co.paralleluniverse.pulsar.core :refer [rcv sfn defsfn snd join spawn-fiber sleep]]
  '[co.paralleluniverse.pulsar.async :as pa]
  '[co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                              register! unregister! self]]

  '[aleph.http :as http]
  '[manifold.stream :as s]
  '[manifold.deferred :as d]
  '[manifold.bus :as bus]

  '[taoensso.timbre :refer [debug info error spy]]
  '[clojure.tools.namespace.repl :as tn]
  '[clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                     <!! >!!
                                     close! put! take! thread timeout
                                     offer! poll! promise-chan
                                     sliding-buffer]])


(def uso (-> system :clients deref first))
(-> (s/description uso) :source :buffer-size)
(s/put! uso "eins")
(s/close! uso)
have a ws-client-auth actor?
- disconnect after timeout
- into incoming and outgoing actors

ws-client-incoming-actor: inti with userID, event-queue and socket source/take/read
ws-client-outgoing-actor: inti with userID, event-queue and socket sink/put/write

have two lists of actors - only for deggugging? - or for communication?

- maintain userID and connection
- read set-state-tag command
- set state tags for following messages

(s/take! uso)
(s/put! uso "vier")



(let [ac (spawn #(loop [] 
                   (receive 
                     [:aa bb] (do (info "out: " bb)
                                  (recur))
                     :else "not found")))
      ]
  (! ac [:aa 123])
  (! ac [:aa 124])
  (! ac [:ac 123])
  (join ac))


(def users [{:user-id "pete" :pw "abc"} 
            {:user-id "paul" :pw "cde"} 
            {:user-id "mary" :pw "fgh"}])


(defn registered-user? [login]
  (-> (partial = login) (filter users) not-empty))


(let [x {:cmd [:auth {:user-id "pete" :pw "abc"}]}]
  (match [x]
         [{:cmd [:auth login]}] (if (registered-user? login) 
                                  (info "user: " (:user-id login)))

         [{:c 3 :d _ :e 4}] :a2
         :else nil))


(def ws-client-incoming-actor 
  "ws-client-incoming-actor"
  (sfn ws-client-incoming-actor [ev-queue]
       (loop [aa 123]

         (receive
           [:append! new-events] (do
                                   (println "eins")
                                   (recur))

           ))))

(def auth-init-actor
  "ws-client-incoming-actor"
  (sfn auth-init-actor [ev-queue user-socket]
       (receive
         [{:cmd [:auth login]}] (if (registered-user? login) 
                                  (info "user: " (:user-id login)))
         :else (println "got it!")
         :after 6000 "timed out!!")))



{:client-id "ab23" :room "green" :aa 12 :bb [1 2 3]}

{:cmd [:auth {:user-id "pete" :pw "abc"}]}

(let [user-socket     (s/stream)
      auth-init-actor (spawn #(receive
                                [{:cmd [:auth login]}] (if (registered-user? login) 
                                                         (info "user: " (:user-id login)))
                                :else (println "got it!")
                                :after 6000 "timed out!!")) 
      ]

  ;; (! auth-init-actor [:foo 12])

  (s/consume #(! auth-init-actor %) user-socket)

  (s/put! user-socket [:foo 23])

  (join auth-init-actor))




