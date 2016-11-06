(require '[rt-comm.incoming.connect-auth :refer [connect-process auth-process]] 
         '[rt-comm.utils.utils :as u :refer [valp]]
         '[rt-comm.init-ws-user :as init-ws-user]

         '[kvlt.core :as kvlt]
         '[kvlt.chan :as kvlc]
         '[promesa.core :as pr]
         '[clj-http.client :as clj-http]
         '[cheshire.core :as json]

         '[co.paralleluniverse.pulsar.core :as pu :refer [rcv channel sfn defsfn snd join fiber spawn-fiber sleep]]
         '[co.paralleluniverse.pulsar.actors :refer [maketag defactor receive-timed receive !! ! spawn mailbox-of whereis 
                                                    register! unregister! self]]
         '[clojure.core.async :as a :refer [pub sub chan <! >! go-loop go alt!! 
                                            <!! >!!
                                            close! mult tap untap put! take! thread timeout
                                            offer! poll! promise-chan
                                            sliding-buffer]]
         '[taoensso.timbre :refer [debug info error spy]])

json/generate-string
json/parse-string

(-> (clht/get ur) :headers)

(def ur "https://api.github.com/users/andreasthoelke")
(def ur "http://spiegel.de")
(def ur "http://localhost:5050/rtc/orders")
(def ur "http://localhost:4040/rtc/orders")

(identity @(kvlt/request!
             {:url ur}))

(-> @(kvlt/request!
       {:url ur
        :as :json})
    :body
    :dynamo)

(-> @(kvlt/request!
       {:url ur
        :as :json}
       )
    :body
    :datomic)



(def pur "http://localhost:5050/rtc/orders/")
(def pur "http://localhost:4040/rtc/orders")

(def pur "http://localhost:5050/rtc/tee")
(def pur "http://localhost:4040/rtc/tee")

(def pur "http://localhost:5050/rtc/abg/")



(def dat {:ticker "fubi"
          :bib 223
          :offer 62
          :qty 84})

(def dat {:aaa "fubiiiiii!"
          :bbb 8888888
          :eeg 4321})

(-> @(kvlt/request!
       {:url pur
        :method :post
        ;; :body   "{\"x\": 1}"
        ;; :as     :string
        ;; :accept-encoding "gzip"
        ;; :content-type :edn
        ;; :form {:hello 'world}
        ;; :as :edn
        ;; :content-type :edn
        ;; :as :auto

        ;; :body ^:kvlt.body/edn dat
        ;; :body ^:kvlt.body/json dat
        ;; :body "einsss"
        ;; :type :json
        :as :json

        ;; :content-type :json
        :form dat
        ;; :body (json/generate-string dat)
        })
    )


{:headers {:server "Aleph/0.4.1", 
           :connection "Keep-Alive", 
           :transfer-encoding "chunked", 
           :date "Sat, 
                 05 Nov 2016 10:50:41 GMT", 
           :content-type "application/json"}, 
 :orig-content-encoding nil, 
 :reason :ok, 
 :status 200, 
 :keep-alive? true, 
 :orig-content-type "application/json; charset=utf-8", 
 :connection-time 0, 
 :body {:ab 234, 
        :cd [22 44], 
        :we {:a 2, 
             :bb nil}}}

(info "-----")

;; A get-request with query-params 
;; sends a map as url encoded query-params
(def pur "http://localhost:5050/rtc/bbb/")
(:body (clj-http/get pur {:query-params {:aa "eins" :cc 333}
                          :as :auto}))

{:status 200, 
 :headers {"Content-Type" "application/json; charset=utf-8", 
           "Server" "Aleph/0.4.1", 
           "Connection" "Close", 
           "Date" "Sat, 
                  05 Nov 2016 12:13:30 GMT", 
           "transfer-encoding" "chunked"}, 
 :body {:cd [22 44], 
        :we {:a "eins", 
             :bb "333"}}, 
 :request-time 35, 
 :trace-redirects ["http://localhost:5050/rtc/bbb/"], 
 :orig-content-encoding nil, 
 :content-type :application/json, 
 :content-type-params {:charset "utf-8"}}



(def pur "http://localhost:5050/rtc/aabb/")
(clj-http/post pur {:form-params {:aa "eins" :cc 333}
                    :as :auto})
{:status 200, 
 :headers {"Content-Type" "application/json; charset=utf-8", 
           "Server" "Aleph/0.4.1", 
           "Connection" "Close", 
           "Date" "Sun, 
                  06 Nov 2016 10:16:10 GMT", 
           "transfer-encoding" "chunked"}, 
 :body {:aa "eins", 
        :bb "333"}, 
 :request-time 60, 
 :trace-redirects ["http://localhost:5050/rtc/aabb/"], 
 :orig-content-encoding nil, 
 :content-type :application/json, 
 :content-type-params {:charset "utf-8"}}

;; Send form params as a json encoded body (POST or PUT)
(def pur "http://localhost:5050/rtc/aabb/")
(:body (clj-http/post pur {:form-params {:aa "eins" :cc 333}
                           :content-type :transit+json ;; or just json
                           :as :auto}))
{:aa "eins", :bb 333}


;; async not working?!
(def pur "http://localhost:5050/rtc/bbb/")
(clj-http/get pur {:query-params {:aa "eins" :cc 333}
                   :content-type :json
                   :as :auto
                   :async? true}
              (fn [resp] (info "bbb response:" resp))
              (fn [ex] (info "aabb ex:" ex)))

;; this is probably just the same
(:body (clj-http/post pur {:body (json/generate-string {:aa "eins" :cc 333})
                           :content-type :json 
                           :as :auto}))


(def pur "http://localhost:5050/rtc/bbb/")
(:body (identity @(kvlt/request!
                    {:url pur
                     :query-params {:aa "eins" :cc 333}
                     :content-type :json
                     :as :auto})))

(def pur "http://localhost:5050/rtc/aabb/")
(:body (identity @(kvlt/request!
                    {:url pur
                     :method :post
                     :form-params {:aa "eins" :cc 333}
                     :content-type :json
                     :as :json
                     })))

(<!! (go (<! (kvlc/request! {:url pur
                             :method :post
                             :form-params {:aa "eins" :cc 333}
                             :content-type :json
                             :as :json
                             }))))




(def res (-> (kvlt/request!
               {:url "http://localhost:5000/echo"
                :query-params {:abcd 200}}) 
             (p/then identity)
             ))

(-> (identity @res)
    :body
    #_:params)

(let [{:keys [status]} @(kvlt/request! {:url "http://localhost:5000/echo"})]
  (is (= status 200)))

(let [va (kvlt/request!
           {:url "http://localhost:5000/echo"
            :query-params {:status 400}})]
  va)

(p/then (kvlt/request!
          {:url "http://localhost:5000/echo"
           :query-params {:status 400}}) 
        println)

(deftest error-middleware-cooperates
  (util/with-result
    (p/branch (kvlt/request!
               {:url "http://localhost:5000/echo"
                :query-params {:status 400}})
      (constantly nil)
      ex-data)
    (fn [{:keys [status headers]}]
      (is= 400 status)
      (is (some keyword? (keys headers))))))

(defmacro show-env [] (keys &env))
(show-env)

(let [band "zeppelin" city "london"] (show-env))

#_(defmacro is= [x y & [msg]]
  (if (:ns &env)
    `(cljs.test/is (= ~x ~y) ~msg)
    `(clojure.test/is (= ~x ~y) ~msg)))

;; (-> m p/promise deref f)

(-> (deref (pr/promise (kvlt/request!
                        {:url (str "http://localhost:" util/local-port "/echo")
                         :method :post
                         :accept-encoding "gzip"
                         :as :edn
                         :content-type :edn
                         :form {:hello "eins"}}
                        ))) 
    :body
    #_:body)

(clht/get (str "http://localhost:" util/local-port "/echo") {:as :auto})
{:status 200, 
 :headers {"Access-Control-Allow-Origin" "*", 
           "Access-Control-Allow-Headers" "Accept, 
                                          Content-Type, 
                                          Authorization", 
           "Access-Control-Allow-Methods" "GET, 
                                          PUT, 
                                          POST, 
                                          DELETE, 
                                          OPTIONS, 
                                          PATCH", 
           "Content-Type" "application/edn", 
           "Server" "Aleph/0.4.1", 
           "Connection" "Close", 
           "Date" "Thu, 
                  18 Aug 2016 09:17:08 GMT", 
           "content-length" "264"}, 
 :body "{:remote-addr \"127.0.0.1\", 
       :params {}, 
       :route-params {}, 
       :headers {\"host\" \"localhost:5000\", 
       \"user-agent\" \"Apache-HttpClient/4.5 (Java/1.8.0_20)\", 
       \"connection\" \"close\", \"accept-encoding\" \"gzip, deflate\"}, :server-port 5000, :form-params {}, :keep-alive? false, :query-params {}, :uri \"/echo\", :server-name \"127.0.0.1\", :query-string nil, :body nil, :scheme :http, :request-method :get}", :request-time 6, :trace-redirects ["http://localhost:5000/echo"], :orig-content-encoding "gzip"}



(defn edn-req [m]
  (util/with-result
    (kvlt/request!
     (merge
      {:url (str "http://localhost:" util/local-port "/echo")
       :method :post
       :accept-encoding "gzip"
       :content-type :edn
       :form {:hello 'world}}
      m))
    (fn [{:keys [status reason headers body] :as resp}]
      (is= 200 status)
      (is= :ok reason)
      (is= "application/edn" (headers :content-type))
      (is= {:hello 'world} (:body body))
      resp)))

(def puu (fn [{:keys [status reason headers body] :as resp}]
           (is= 200 status)
           (is= :ok reason)
           (is= "application/edn" (headers :content-type))
           (is= {:hello 'world} (:body body))
           ))

(def puu (fn [aa]
           aa))


(-> (kvlt/request!
      {:url (str "http://localhost:" util/local-port "/echo")
       :method :post
       :as :edn
       :accept-encoding "gzip"
       :content-type :edn
       :form {:hello 'world}}
      )
    deref
    puu)

(-> 2
    inc
    puu)

(is= 3 4)

(deftest edn-as-edn
  (edn-req {:as :edn}))
(deftest edn-as-auto
  (edn-req {:as :auto}))

(edn-as-auto)

(defn un-byte-array [x]
  #? (:clj  (map identity x)
      :cljs (if (= *target* "nodejs")
              (for [i (range (.. x -length))]
                (.readInt8 x i))
              (let [x (js/Int8Array. x)]
                (for [i (range (.. x -length))]
                  (aget x i))))))

(def hexagram-bytes [-2 -1 -40 52 -33 6])
(def hexagram-byte-array
  #? (:clj  (byte-array hexagram-bytes)
      :cljs (if (= *target* "nodejs")
              (js/Buffer.    (clj->js hexagram-bytes))
              (js/Int8Array. (clj->js hexagram-bytes)))))

(def byte-req
  {:url (str "http://localhost:" util/local-port "/echo/body?encoding=UTF-16")
   :method :post
   :content-type "text/plain"
   :character-encoding "UTF-16"
   :body hexagram-byte-array})

(deftest ^{:kvlt/skip #{:phantom}} bytes->bytes
  (util/with-result
    (kvlt/request! (assoc byte-req :as :byte-array))
    (fn [{:keys [body] :as resp}]
      (is= (un-byte-array body) hexagram-bytes))))

(deftest jumbled-middleware
  (util/with-result
    (kvlt/request!
     {:headers    {"X-HI" "OK" :x-garbage "text/"}
      :url        (str "http://localhost:" util/local-port "/echo")
      :accept     :text/plain
      :basic-auth ["moe@nervous.io" "TOP_SECRET"]
      :query      {:Q :two}
      :as         :auto})
    (fn [{:keys [body] :as resp}]
      (let [{:keys [headers] :as req} (keywordize-keys body)]
        (is (headers :authorization))
        (is= "OK" (headers :x-hi))
        (is= "text/plain" (headers :accept))
        (is= {:Q ":two"}  (req :query-params))))))

#? (:clj
    (deftest deflate
      (util/with-result
        (kvlt/request!
         {:url (str "http://localhost:" util/local-port "/echo")
          :accept-encoding :deflate
          :body   "Hello"
          :type   :edn
          :method :post
          :as     :edn})
        (fn [{{:keys [headers body]} :body}]
          (is= "deflate" (headers "accept-encoding"))
          (is= 'Hello body)))))

(defn json-req []
  (kvlt/request!
   {:url    (str "http://localhost:" util/local-port "/echo/body")
    :method :post
    :body   "{\"x\": 1}"
    :as     :json}))

#? (:clj
    (deftest json-without
      (is (try
            @(json-req)
            nil
            (catch Exception e
              true)))))

#? (:clj
    (deftest json-with
      (with-redefs [kvlt.platform.util/parse-json (constantly {:x 1})]
        (is= {:x 1} (:body @(json-req)))))
    :cljs
    (deftest json-with
      (util/with-result (json-req)
        (fn [{:keys [body]}]
          (is= {:x 1} body)))))

(defn responder [resp]
  (reduce
   #(%2 %1)
   (fn [req]
     (p/resolved
      (with-meta resp {:kvlt/request req})))
   kvlt/default-middleware))

(defmethod kvlt.middleware/as-type :xxx [_]
  (throw #? (:clj (Exception. "LOL JVM") :cljs (js/Error. "OK JS"))))

(deftest parse-error-preserves-existing
  (let [request! (responder {:status 400 :body "..." :error :http-error})]
    (util/with-result
      (p/branch
        (request! {:url "http://localhost"
                   :as  :xxx})
        (constantly nil)
        ex-data)
      (fn [{e :error}]
        (is= e :http-error)))))

(deftest parse-error
  (let [request! (responder {:status 200 :body "..."})]
    (util/with-result
      (p/branch
        (request! {:url "http://localhost"
                   :as  :xxx})
        (constantly nil)
        ex-data)
      (fn [{e :error}]
        (is= e :middleware-error)))))

(deftest non-empty-head
  (let [request! (responder
                  {:status 200
                   :body   #? (:clj (byte-array []) :cljs "")
                   :headers {:content-length   200
                             :content-encoding "gzip"}})]
    (util/with-result (request! {:url "http://rofl" :method :head})
      (fn [resp]
        (is (empty? (resp :body)))
        (is= (resp :status) 200)))))


