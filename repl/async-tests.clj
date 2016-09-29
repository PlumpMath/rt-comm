(require 
  '[dev :refer [system db-conns reset]]
  '[rt-comm.api :refer [add-order! find-orders find-all-orders]]
  '[rt-comm.utils.logging :as logging]

  '[clojure.core.match :refer [match]]

  '[co.paralleluniverse.pulsar.core :as p :refer [rcv try-rcv sfn defsfn snd join fiber spawn-fiber sleep]]
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


;; core.async
(defn batch-rcv-ev-colls [ch]
  "Poll available event collections from ch and batch
  them into one event collection. Non-blocking."
  (go-loop [v []
            x (a/poll! ch)]
           (if-not x
             v
             (recur (into v x)
                    (a/poll! ch)))))

;; TEST CODE:
(def c1 (a/chan 6))
(<!! (batch-rcv-ev-colls c1))
(>!! c1 [2 3 4])
(>!! c1 [5 6 7])


;; Manifold
(defn batch-rcv-ev-colls [in-stream]
  "Try-take! available event collections from in-steam and batch
  them into one event collection. Non-blocking."
  (d/loop [v []]
    (d/chain (s/try-take! in-stream 0)
             #(if-not % 
                v 
                (d/recur (into v %))))))


;; TEST CODE:
(def s1 (s/stream 6))
(batch-rcv-ev-colls s1)
(s/put! s1 [2 3 4])
(s/put! s1 [5 6 7])


;; Pulsar  
(defn batch-rcv-ev-colls [ch]
  "Rcv available event collections from ch and batch
  them into one event collection. Non-blocking."
  (loop [v []
         x (p/try-rcv ch)]
    (if-not x
      v
      (recur (into v x)
             (p/try-rcv ch)))))

;; TEST CODE:
(def c1 (p/channel 6))
(batch-rcv-ev-colls c1)
(snd c1 [2 3 4])
(snd c1 [5 6 7])
(rcv c1)





(def ev-queue (-> system :event-queue :events-server))

(! ev-queue [:append! [{:aa 23}]])


(defsfn incoming-ws-user-process []
  (loop [v []]

     

    (when (> 5 (count v))
      (recur (conj v (rcv c1))))))




(def fi1 (spawn-fiber (fn [] (loop [v []]
                               (info v)
                               (when (> 5 (count v))
                                 (recur (conj v (rcv c1))))))))


(def fi1 (fiber (loop [v []]
                  (info v)
                  (when (> 5 (count v))
                    (recur (conj v (rcv c1)))))))


(defn batch-awail-ev-colls [ch]
  "Rcv available event collections from ch and batch
  them into one event collection. Non-blocking."
  (loop [v []
         x (p/try-rcv ch)]
    (if-not x
      v
      (recur (into v x)
             (p/try-rcv ch)))))

(defsfn batch-rcv-ev-colls [ch]
  "Rcv available event collections from ch and batch
  them into one event collection. Blocks/waits for first msg."
  (loop [v []
         x (p/rcv ch)] ;; pause for first new msgs-coll
    (if-not x
      v
      (recur (into v x)
             (p/try-rcv ch))))) ;; consume other msgs-colls if available



(def c1 (p/channel 6))

(rcv-avail c1)

(snd c1 [2 3 4])
(snd c1 [5 6 7])

(rcv c1)



(def @ee (spawn-fiber #(loop [n (int 0)
                             res []]
                        (if (< n 10)
                          (do 
                            ;; (rcv cc n)
                            (recur (inc n) (conj res (rcv cc))))
                          res))))
(count [])
(info :ab)

(dotimes [i 3]
  (snd cc i))
(snd c1 65)



(def evts [{:time 10 :loc [14 10]} 
           {:time 11 :loc [9 14]} 
           {:time 12 :loc [8 11]}])

(defn assoc-user-id [user-id m]
  (assoc m :user-id user-id))

(partial assoc-user-id "pete")

(defn assoc-user-id [user-id]
  (fn [m] (assoc m :user-id user-id)))

(assoc-user-id "pete")

(map (assoc-user-id "pete") evts)


(->> evts
     s/->source
     (s/map (assoc-user-id "pete"))
     s/stream->seq)


(def c1 (p/channel 3))


(def fi1 (spawn-fiber (fn [] (loop [v []]
                               ;; (info (conj v (rcv c1))) 
                               (when (> 3 (count v)) 
                                 (recur (conj v (rcv c1))))))))

(def fi2 (sfn []
              (loop [v []]
                (info (conj v (rcv c1))) 
                (when (> 3 (count v)) 
                  (recur (conj v (rcv c1)))))))

(fi2)

(let [ch (p/channel 10 :displace)
      task (sfn [] 
                (let [ch (p/ticker-consumer ch)]
                  (loop [prev -1]
                    (let [m (rcv ch)]
                      (when m
                        (assert (> m prev)) ;(fact m => (gt? prev))
                        (recur (long m)))))))
      f1 (spawn-fiber task)
      t1 (p/spawn-thread task)
      f2 (spawn-fiber task)
      t2 (p/spawn-thread task)
      f3 (spawn-fiber task)
      t3 (p/spawn-thread task)
      f4 (spawn-fiber task)
      t4 (p/spawn-thread task)]
  (sleep 100)
  (dotimes [i 1000]
    (sleep 1)
    (snd ch i))
  (p/close! ch)
  (join (list f1 t1 f2 t2 f3 t3 f4 t4))
  )

(let [ch (p/channel)
            fiber (spawn-fiber
                    (fn []
                      (let [m1 (rcv ch)
                            m2 (rcv ch)
                            m3 (rcv ch)
                            m4 (rcv ch)]
                        (list m1 m2 m3 m4))))]
        (sleep 20)
        (snd ch "m1")
        (sleep 20)
        (snd ch "m2")
        (sleep 20)
        (snd ch "m3")
        (p/close! ch)
        (snd ch "m4")
        (join fiber))


(dotimes [i 3]
  (snd c1 i))



(snd c1 10)
(rcv c1)



(when (try-rcv in-chan))
conj all vecs into one vec

s/try-take!

wie gross in ist in buffer und wann [[berlaufefn?]]


(deref d1)

(defn aw-d [d cb]
  (cb @d))

(defn aw-d [d cb]
  (d/on-realized d cb cb))

(defn aw [p cb]
  (await (fn [v cb]
           ())))

(defn p-deref [p] (await #(% @p)))

(defsfn deref* [p] (await (fn [d cb]
                          (d/on-realized d cb cb))
                        p))

(defn deref* [d] 
  "Deref the given manifold.deferred (using a callback), just
  blocking the current fiber, not the current thread."
  (pl/await (fn [df cb]
             (d/on-realized df cb cb))
           d))

(def deref2 
  (sfn deref2 [d]
       (await (fn [p cb]
                (d/on-realized p cb cb))
              d)))

(defsfn deref3 [d]
  (pl/await (fn [p cb]
           (d/on-realized p cb cb))
         d))

(def d1 (d/deferred))
(type d1)
(def r1 (fiber (await #(d/on-realized %1 %2 %2) d1)))

(def r1 (fiber (await aw-d d1)))
(def r1 (fiber (deref* d1)))
(def r1 (fiber (deref3 d1)))


(def r1 (fiber (some-> d1 cf1)))


(d/success! d1 :aa)

(deref r1)

(defn cf1 [d2] 
  (-> d2 ;; Wait for first message/auth-message!
      cf2
      ))

(defn cf2 [d3] 
  (-> d3 ;; Wait for first message/auth-message!
      deref3
      ))


(def d1 (d/deferred))
(def fu1 (fiber (cf1 d1)))
(d/success! d1 :eins)
(deref fu1)




(def @r1 (fiber (await f1 2)))

(defn f1 [x cb]
  (cb x))

;; (let [x '(1 2)]
;;   (match [x]
;;     [([1] :seq)] :a0
;;     [([1 & r] :seq)] [:a1 r]
;;     :else nil))

(let [x [1 2]]
  (match [x]
    [[1 3]] :a0
    [[1 & r]] [:a1 r]
    :else 432))

(let [x [:ada 2]]
  (match [x]
    [[:aa & r]] [:a1 r]
    [[_ ld]] [:a0 ld]
    :else 432))

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

(maketag)



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




;; -------------------------------------------------------------------------------

(def ts (s/stream))

(s/put! ts "eins")
(s/take! ts)

(def ab (spawn
          #(receive
             [:foo] nil
             :else (println "got it!")
             :after 30 :timeout)))


(def aa (fiber (+ 2 5)))
@aa

(def bb (d/deferred))
(deliver bb "eins")
@bb

(def bc (d/chain
          aa
          (fn [x] (inc x))))

(def ab (fiber (str @aa)))
(def ab (fiber (str @bb)))
@ab


;; -------------------------------------------------------------------------------

(let [v0 (pl/promise)
      v1 (pl/promise)
      v2 (pl/promise #(+ @v1 1))
      v3 (pl/promise #(+ @v1 @v2))
      v4 (pl/promise #(* (+ @v3 @v2) @v0))]
  (Strand/sleep 0)
  (deliver v1 1)
  ;; all dependant strand code runs first before main thread continues
  ;; now all but the first and the last promise is deliverd
  (mapv realized? [v0 v1 v2 v3 v4]) ; => [false true true true false]
  (deliver v0 2)
  @v4) ; => 10


(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})


(def users [{:user-id "pete" :pw "abc"} 
            {:user-id "paul" :pw "cde"} 
            {:user-id "mary" :pw "fgh"}])

(defn registered-user? [login]
  (-> (partial = login) (filter users) not-empty))

(defn check-authentification [auth-message]
  (match [auth-message]
         [{:cmd [:auth login]}] (if (registered-user? login) 
                                  [:success (:user-id login)] :failed)
         [:false] :conn-closed
         :else :no-auth-cmd))

(check-authentification [:free "eins"])

(do (def conn-d (d/deferred)) ;; wait for connection
    (def auth   (d/chain conn-d  ;; when connected ..
                         #(s/take! %) ;; take message from conn
                         check-authentification ;; just pass into an fn that returns 
                         ))
    (def err    (d/catch auth (fn [ex] non-websocket-request)))
    (def tim    (d/timeout! auth 30000 :timeout!))

    (def stree  (s/stream))
    ;; (def rt (d/success! conn-d stree)) ;; return the user-socket after connection - 
    ;; (def rt (d/error! conn-d (Exception. "err message.."))) ;; or error 
    )

(s/close! stree)
(identity [conn-d auth tim])
(def rt (d/success! conn-d stree))
(def rt2 (s/put! stree {:cmd [:auth {:user-id "pete" :pw "abc"}]}))

()


(do (def conn-d (d/deferred)) 
    (def auth (-> (d/chain conn-d       ;; Async 1: Wait for connection
                           #(s/take! %) ;; Async 2: Wait for first message
                           check-authentification) ;; Returns :failed, [:success user-id], ..
                  (d/timeout! 10000 :timed-out) ;; Connection and auth must be competed within timeout
                  (d/catch (fn [e] :conn-error)))) ;; Catch non-WS requests. Other errors? 

    (def send-msg-close #(d/future (do (s/put!   @conn-d %)
                                       (s/close! @conn-d)))) 
    ;; 2. AUTH:
    (def re-auth (d/chain auth 
                          #(do (info "auth:" %) (identity %)) 
                          #(match [%] ;; Handle outcome of auth process
                                  [:conn-error]  non-websocket-request ;; Return Http response on error
                                  [:timed-out]   (send-msg-close "Authentification timed out! Disconnecting.")
                                  [:no-auth-cmd] (send-msg-close "No auth. command received! Disconnecting.")
                                  [:failed]      (send-msg-close "User-id - password login failed! Disconnecting.")
                                  [:conn-closed] (info "Ws client closed connection before auth.")

                                  [[:success user-id]]     (d/future (do (s/put! @conn-d "Login success!")
                                                                         (info (format "Ws user-id %s loged in." user-id))
                                                                         ;; (curried-incoming-out-fn @conn-d user-id)
                                                                         "Ws auth success return val"))
                                  :else (error "Ws connection: Invalid auth response!" %))))
    (def stree  (s/stream))
    (def rt (d/success! conn-d stree)) ;; return the user-socket after connection - 
    ;; (def rt (d/error! conn-d (Exception. "err message.."))) ;; or error 

    )

(def s1 (s/stream))
(def s2 (s/stream))

(def rr (s/connect s1 s2))

(s/consume #(prn %) s1)



(do (prn "ein"))

(def rt2 (s/put! stree {:cmd [:auth {:user-id "pete" :pw "abc"}]}))
(s/put! stree "rei")
(def me (d/future (s/close! stree)))
;; HIER
(let [conn-d (d/deferred)
      auth   (-> (d/chain conn-d       ;; Async 1: Wait for connection
                          #(s/take! %) ;; Async 2: Wait for first message
                          check-authentification) ;; Returns :failed, [:success user-id], ..
                 (d/timeout! 10000 :timed-out) ;; Connection and auth must be competed within timeout
                 (d/catch (fn [e] :conn-error))) ;; Catch non-WS requests. Other errors? 

      send-msg-close #(d/future (do (s/put!   @conn-d %) 
                                    (s/close! @conn-d)))] 

  (d/chain auth #(case %
                   [:conn-error]  non-websocket-request ;; Return Http response immediately
                   [:timed-out]   (send-msg-close "Authentification timed out! Disconnecting.")
                   [:no-auth-cmd] (send-msg-close "No auth. command received! Disconnecting.")
                   [:failed]      (send-msg-close "User-id - password login failed! Disconnecting.")
                   [:conn-closed] (info "Ws client closed connection before auth.")
                   [[:success user-id]]     (d/future (do (s/put! @conn-d "Login success!")
                                                          (curried-incoming-out-fn @conn-d)
                                                          (info )))
                   (error "Ws connection: unexpected case!" %)
                   )))

send different messeges for timeout and failed auth
disconnect in botth cases.
handler should return conn-d which may yealed the "connection faild" response.


(do (def conn-d (d/deferred)) ;; wait for connection
    (def auth   (d/chain conn-d  ;; when connected ..
                         #(s/take! %) ;; take message from conn
                         check-authentification ;; just pass into an fn that returns 
                         ))
    (def err    (d/catch auth (fn [ex] non-websocket-request)))
    (def tim    (d/timeout! auth 10000 :timeout!))

    (def stree  (s/stream))
    ;; (def rt (d/success! conn-d stree)) ;; return the user-socket after connection - 
    ;; (def rt (d/error! conn-d (Exception. "err message.."))) ;; or error 
    )



(let [conn-d (d/deferred) 
      auth   (d/chain conn-d 
                      #(s/take! %)
                      check-authentification
                      (d/timeout! 4000 false)
                      )    
      ])

(def aajj (d/timeout! 4000 :timeout))


(-> (d/deferred)
    (d/chain #(s/take! %)
             check-authentification
             (d/timeout! 4000 false)
             )
    (d/catch (fn [] non-websocket-request)))

(let [conn-d (-> (http/websocket-connection request) 
                 (d/catch (fn [_] nil))) 
      ])



(defn ein [a] (if a ))

(def v1 (d/deferred))
(def v2 (d/deferred))
(def v11 (d/chain v1 
                  #(do (info "ouut:" %) (inc %)) 
                  #(d/future (info "b-success:" % @v2))
                  ))

(def v2 (d/on-realized v1 #(info "success:" %) #(info "err:" %)))

(def t1 (d/timeout! v1 4000 :timeout!))

(d/success! v1 88)
(d/success! v2 10000)

(def s1 (s/stream))

(def ta (s/take! s1))
@ta

(def tt (-> (s/take! s1)
            (d/timeout! 4000 :timed-out)))



(def p1 (s/put! s1 5))

(def vs1 (s/take! s1))

(def h1 (pl/promise #(identity (s/take! s1))))

(def h1 (pl/promise #(identity 1)))

(def p1 (pl/promise))
(def p2 (pl/promise #(identity @v1)))
@p2
(deliver p1 :ree)

(def af (first @cls))
(indentity abc)

(count @cls)
(s/put! af "drei")



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


