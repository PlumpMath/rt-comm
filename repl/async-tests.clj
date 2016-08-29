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

(import '[java.util.concurrent TimeUnit TimeoutException ExecutionException]
        '[co.paralleluniverse.common.util Debug]
        '[co.paralleluniverse.strands Strand]
        '[co.paralleluniverse.fibers Fiber])

(maketag)


;; (let [x '(1 2)]
;;   (match [x]
;;     [([1] :seq)] :a0
;;     [([1 & r] :seq)] [:a1 r]
;;     :else nil))
;;
;; (let [x [1 2]]
;;   (match [x]
;;     [[1 3]] :a0
;;     [[1 & r]] [:a1 r]
;;     :else 432))
;;
;; (let [x {:a 6 :c 232 :b 1}]
;;   (match [x]
;;     [{:a gd :b 1}] [:a0 gd]
;;     [{:a 1 :b 1}] :a1
;;     [{:c 3 :d _ :e 4}] :a2
;;     :else nil))
;;
;; (let [x {:a 1 :b 1}]
;;   (match [x]
;;     [{:a _}] :a0
;;     :else :no-match))
;;
;; (let [x {:a 1 :b 2}]
;;   (match [x]
;;     [({:a _ :b 2} :only [:a :b])] :a0
;;     [{:a 1 :c _}] :a1
;;     [{:c 3 :d _ :e 4}] :a2
;;     :else nil))
;;
;; (let [x {:a 1 :b 2 :c 3}]
;;   (match [x]
;;     [({:a _ :b 2} :only [:a :b])] :a0
;;     [{:a 1 :c _}] :a1
;;     [{:c 3 :d _ :e 4}] :a2
;;     :else nil))
;;
;; (let [x 4 y 6 z 9]
;;   (match [x y z]
;;     [(:or 1 2 3) _ _] :a0
;;     [4 (:or 5 6 7) _] :a1
;;     :else nil))
;;
;; (match [{:a {:b :c}}]
;;   [{:a {:b nested-arg}}] nested-arg)
;;
;; (match [{:a {:b :c}}]
;;   [{:a {:b nested-arg}}] nested-arg)
;;
;;
;;
;; (let [n 0]
;;   (match [n]
;;     [(1 :<< inc)] :one
;;     [(2 :<< dec)] :two
;;     :else :no-match))


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



(fact "The spawn macro will evaluate arguments by value"
      (let [a (spawn #(do
                       (spawn (fn [parent] (! parent :something)) @self)
                       (receive [m] :something m :after 1000 nil)))]
        (join a))
      => :something)

(fact "When actor throws exception then join throws it"
      (let [actor (spawn #(throw (Exception. "my exception")))]
        (join actor))
      => (throws Exception "my exception"))

(fact "When actor returns a value then join returns it"
      (let [actor (spawn #(+ 41 1))]
        (join actor))
      => 42)

(fact "actor-receive"
      (fact "Test simple actor send/receive"
            (let [actor (spawn #(receive))]
              (! actor :abc)
              (join actor)) => :abc)
      (fact "Test receive after sleep"
            (let [actor
                  (spawn #(let [m1 (receive)
                                m2 (receive)]
                            (+ m1 m2)))]
              (! actor 13)
              (Thread/sleep 200)
              (! actor 12)
              (join actor)) => 25)
      (fact "When simple receive and timeout then return nil"
            (let [actor
                  (spawn #(let [m1 (receive-timed 50)
                                m2 (receive-timed 50)
                                m3 (receive-timed 50)]
                           [m1 m2 m3]))]
              (! actor 1)
              (Thread/sleep 20)
              (! actor 2)
              (Thread/sleep 100)
              (! actor 3)
              (fact (.isFiber (LocalActor/getStrand actor)) => true)
              (join actor) => [1 2 nil]))
      (fact "When simple receive (on thread) and timeout then return nil"
            (let [actor
                  (spawn
                    :scheduler :thread
                    #(let [m1 (receive-timed 50)
                                m2 (receive-timed 50)
                                m3 (receive-timed 50)]
                            [m1 m2 m3]))]
              (! actor 1)
              (Thread/sleep 20)
              (! actor 2)
              (Thread/sleep 100)
              (! actor 3)
              (fact (.isFiber (LocalActor/getStrand actor)) => false)
              (join actor) => [1 2 nil])))

(fact "matching-receive"
      (fact "Test actor matching receive 1"
            (let [actor (spawn
                          #(receive
                             :abc "yes!"
                             :else "oy"))]
              (! actor :abc)
              (join actor)) => "yes!")
      (fact "Test actor matching receive 2"
            (let [actor (spawn
                          #(receive
                             :abc "yes!"
                             [:why? answer] answer
                             :else "oy"))]
              (! actor [:why? "because!"])
              (join actor)) => "because!")
      (fact "Test actor matching receive 3"
            (let [res (atom [])
                  actor (spawn
                          #(receive
                            [x y] (+ x y)))]
              (! actor [2 3])
              (join actor)) => 5)
      (fact "When matching receive and timeout then run :after clause"
            (let [actor
                  (spawn
                    #(receive
                       [:foo] nil
                       :else (println "got it!")
                       :after 30 :timeout))]
              (Thread/sleep 150)
              (! actor 1)
              (join actor)) => :timeout))

(fact "selective-receive"
      (fact "Test selective receive1"
            (let [res (atom [])
                  actor (spawn
                          #(dotimes [i 2]
                             (receive
                               [:foo x] (do
                                          (swap! res conj x)
                                          (receive
                                            [:baz z] (swap! res conj z)))
                               [:bar y] (swap! res conj y)
                               [:baz z] (swap! res conj z))))]
              (! actor [:foo 1])
              (! actor [:bar 2])
              (! actor [:baz 3])
              (join actor)
              @res) => [1 3 2])
      (fact "Test selective ping pong"
            (let [actor1 (spawn
                           #(receive
                             [from m] (! from @self (str m "!!!")))) ; same as (! from [@self (str m "!!!")])
                  actor2 (spawn
                           (fn []
                             (! actor1 @self (receive)) ; same as (! actor1 [@self (receive)])
                             (receive
                               [actor1 res] res)))]
              (! actor2 "hi")
              (join actor2)) => "hi!!!"))


(defn append! [events]
  (info [:append! events]))







(def c (chan 1))

(def sub-c (pub c :route))

(def cx (chan 1))

(sub sub-c :up-stream cx)

(def cy (chan 1))

(sub sub-c :down-stream cy)

(go-loop [v (<! cx)]
         (info "Got something coming up!" v))

(go-loop [v (<! cy)]
         (info "Got something going down!" v))

(put! c {:route :up-stream 
         :data 123})

(put! c {:route :down-stream 
         :data 123})

#_(defn chat-handler
  [req]
  (d/let-flow [conn (d/catch
                      (http/websocket-connection req)
                      (fn [_] nil))]

    (if-not conn

      ;; if it wasn't a valid websocket handshake, return an error
      "non .." ;;non-websocket-request

      ;; otherwise, take the first two messages, which give us the chatroom and name
      (d/let-flow [room (s/take! conn)
                   name (s/take! conn)]

        ;; take all messages from the chatroom, and feed them to the client
        (s/connect
          (bus/subscribe chatrooms room)
          conn)

        ;; take all messages from the client, prepend the name, and publish it to the room
        (s/consume
          #(bus/publish! chatrooms room %)
          (->> conn
            (s/map #(str name ": " %))
            (s/buffer 100)))))))

#_(defn chat-handler
  [req]
  (let [conn @(d/catch
                (http/websocket-connection req) ;; a deferred that will return a socket/stream
                (fn [_] nil))] ;; should this connection-deferred throw an error, the deferred will return nil

    (if-not conn

      ;; if it wasn't a valid websocket handshake, return an error
      "invalid request" ;;non-websocket-request

      (let [d-room (s/take! conn) ;; two takes sitting and waiting in sequence on the same stream -> could test this.
            d-name (s/take! conn)])

        ;; take all messages from the chatroom, and feed them to the client
        (s/connect
          (bus/subscribe chatrooms room)
          conn)

        ;; take all messages from the client, prepend the name, and publish it to the room
        (s/consume
          #(bus/publish! chatrooms room %)
          (->> conn
               (s/map #(str name ": " %))
               (s/buffer 100))))))


(append! {:time 123 :msg [:eins "wee"]})

(def st (s/stream 4))
(def stb (s/buffer 4 st))
(s/close! st)
(s/description st)

(doseq [ms (map (partial str "Idx: ") (range 6))]
  (s/put! st ms))

(s/stream->seq st)

(s/description stb)

(def rd (s/put! st [1 8]))

(def rrd (s/take! stb))
(def rrd (s/take! st))

(def cd (s/consume append! st)) ; o <--o

(def d (d/deferred))

(d/success! d 5)

(s/connect )

(Callback. )

@d

(defn prt [] (info "drained"))

(def aa (->> [1 2 3]
             s/->source
             (s/map inc)
             ;; (#(s/on-drained % prt))
             ;; s/stream->seq
             ))

(s/on-drained aa prt)

(def sa (s/stream->seq aa))

(s/take! aa)

(def uso (-> system :clients deref first))
(-> (s/description uso) :source :buffer-size)

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


;; -------------------------------------------------------------------------------

(def ts (s/stream))

(s/put! ts "eins")
(s/take! ts)

(def ab (spawn
          #(receive
             [:foo] nil
             :else (println "got it!")
             :after 30 :timeout)))


(def ws-client-incoming-actor 
  "ws-client-incoming-actor"
  (sfn ws-client-incoming-actor [ev-queue]
       (loop [aa 123]

         (receive
           [:append! new-events] (do
                                   (println "eins")
                                   (recur))

           ))))


(def au (spawn #(receive
                  [:foo aa] (info aa)
                  :else (println "got it!")
                  :after 6000 "timed out!!")))

(def mb (mailbox-of au))

(let [user-socket     (s/stream)
      auth-init-actor (spawn #(receive
                                [:foo aa] (info aa)
                                :else (println "got it!")
                                :after 6000 "timed out!!")) 
      ]

  ;; (! auth-init-actor [:foo 12])

  (s/consume #(! auth-init-actor %) user-socket)

  (s/put! user-socket [:foo 23])

  (join auth-init-actor))


{:client-id "ab23" :room "green" :aa 12 :bb [1 2 3]}


(def af (first @cls))
(indentity abc)

(count @cls)
(s/put! af "drei")

(defn map
  "Equivalent to Clojure's `map`, but for streams instead of sequences."
  ([f s]
    (let [s' (stream)]
      (connect-via s
        (fn [msg]
          (put! s' (f msg)))
        s'
        {:description {:op "map"}})
      (source-only s')))
  ([f s & rest]
    (map #(apply f %)
      (apply zip s rest))))



(defn poll! [s]
  (s/description ))

;; take all and conj until block?

(d/on-realized d 
             (fn [x] (println "succ!!" x))
             (fn [x] (println "err!!" x)))


(d/chain d inc inc #(println "res.." %))

(d/success! d :foo)

(d/chain d inc inc inc #(println "x + 3 =" %))

(d/success! d 0)

(def d (d/deferred))

@(d/timeout!
     (d/future (Thread/sleep 1000) :foo)
     100
     :bar)

@(d/zip (future 1) (future 2) (future 3))
(-> d
    (d/chain dec #(/ 1 %))
    (d/catch Exception #(println "whoops, that didn't work:" %)))

(d/success! d 1)
(d/chain d
         #(future (inc %))
         #(println "the future returned" %))

(d/success! d 0)
< the future returned 1 >
true

(let [a (future 1)]
  (d/let-flow [b (future (+ a 1))
             c (+ b 1)]
    (+ c 1)))

(let-flow [b (future 1)
           c (+ b 1)]
  (+ c 1))


;; A reducing function takes: Accumulator, Collection!!
(reduce + 4 [1 2 3])
(reduce conj [:a :b] [1 2 3])

(into []
      (comp (map inc) ;; map is a transducer factory!
            (filter odd?))
      (range 10))
[1 3 5 7 9]

;; comp: works backwards! <<-- .. right to left
((comp str inc) 0)

(defn xcycle-v1 [rf]
  (let [coll (volatile! [])]
    (fn
      ([] (rf))
      ([result] (rf result))
      ([result input]
       (vswap! coll conj input)
       (rf result input)))))

(into [] xcycle-v1 [1 2 3])


(def aa (s/stream))
(def bb (s/stream))

(def cc (s/splice aa bb))

(s/put! cc :putted4)

(s/put! aa :putted6)
(s/put! bb :putted-b-6)

(s/take! aa)
(s/take! bb)

(-> aa s/description :pending-puts)
(-> bb s/description :sink :pending-puts)
(-> cc s/description :sink :pending-puts)


(info "eins")

;;
;; (s/put! s 6)
;;
;; (s/close! s)
;;
;; ;; it 
;; @(s/take! s)
;;
;; (s/try-take! s :foo 2000 :timeout)
;;
;; (s/consume #(prn 'message! %) s)
;;
;; @(s/put! s 11)
;;
;; (->> [1 2 3]
;;      s/->source
;;      (s/map inc)
;;      s/stream->seq)
;;

(def tee (s/periodically 1000 #(rand-int 16)))

(def too (s/consume #(prn 'something!! %) tee))

(s/close! tee)


;; Produce a stream of numbers
(let [sent (atom -1)]
  (->> (s/periodically 500 #(str (swap! sent inc)))
       (s/transform (take 10))
       (s/consume #(prn 'out!! %))))

;;
;;
;; (def taa (->> (s/periodically 1000 #(rand-int 8))
;;               (s/consume #(prn 'something! %))))
;;
;; (take 22 (repeatedly #(rand-int 11)))
;;
;; ()
;;


(import 'java.util.concurrent.ArrayBlockingQueue)

(defn producer [c]
  (<<< "Taking a nap")
  (Thread/sleep 5000)

  (<<< "Now putting a name in queue...")
  (.put c "Leo"))

(defn consumer [c]
  (<<< "Attempting to take value from queue now...")
  (<<< (str "Got it. Hello " (.take c) "!")))


(def chan (ArrayBlockingQueue. 10))

(future (consumer chan))
(future (producer chan))




(defn websocket->async
  "Opens a websocket connection from the given request
  and uses the provided core.async channels to handle
  the input and provide the output."
  [request in-ch out-ch]
  (let [stream @(http/websocket-connection request)]
    (stream/connect stream in-ch)
    (stream/connect out-ch stream)))

(defn async->websocket
  "Opens a websocket connection to the given url and
  uses the provided core.async channels to handle the
  input and provide the output."
  [url in-ch out-ch]
  (let [stream @(http/websocket-client url)]
    (stream/connect stream in-ch)
    (stream/connect out-ch stream)))


