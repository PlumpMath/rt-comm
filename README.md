# rt-comm

Playground app exploring a clojure stack for scalable real-time communication, featuring component, aleph, pulsar and DynamoDB.

### usage

1. Start DynamoDB local. (e.g. `java -Djava.library.path=./DynamoDBLocal_lib -jar ..\DynamoDB\DynamoDBLocal.jar -sharedDb`). See `dev/resources/config.edn` for configuration. Datomic-free should work without further setup.
2. `lein repl` in the cloned repo.
3. Tail `log.txt` to view timbre logging.
4. Run `(in-ns 'dev)` and then `(go)` to start the system. Use `(reset)` or `(refresh)` to reset the system/reload code.
5. You can try out the routes defined in `api.clj`. Note that operations are dublicated for Dynamo and Datomic.
6. You can try out the `websockets.clj` component by connection two browser clients (e.g. use `http://www.websocket.org/echo.html` with `ws://localhost:4242/ws`). TODO: CLJS client.
7. Test the event-queue by running the test code in `event-queue.clj`, e.g. `(<>! :c3 :next)`.
