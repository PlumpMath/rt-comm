(require 
  '[dev :refer [system db-conns reset]]
  '[rt-comm.api :refer [add-order! find-orders find-all-orders]]
  '[rt-comm.utils.logging :as logging]

  '[aleph.http :as http]
  '[manifold.stream :as s]
  '[manifold.deferred :as d]
  '[manifold.bus :as bus]

  '[taoensso.timbre :refer [debug info error spy]]
  '[clojure.tools.namespace.repl :as tn]
  '[clojure.core.async :as a :refer [chan <! >! go-loop go alt!! 
                                     <!! >!!
                                     close! put! take! thread timeout
                                     offer! poll! promise-chan
                                     sliding-buffer]])



(def d (d/deferred))

(d/success! d 5)

@d

(d/on-realized d 
             (fn [x] (println "succ!!" x))
             (fn [x] (println "err!!" x)))


(d/chain d inc inc #(println "res.." %))

(success! d 5)

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


