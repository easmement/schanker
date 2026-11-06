(ns atndev.server-test
  "Locks in the JSON API the web UI depends on: response shape, error cases, and the
   localhost-only guard. Calls the Ring handler directly; no network. Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [cheshire.core :as json]
            [atndev.server :as server]))

(defn- post [sentence & {:keys [host] :or {host "localhost:3100"}}]
  (let [resp (server/app {:request-method :post :uri "/api/parse"
                          :headers {"host" host}
                          :body (java.io.ByteArrayInputStream.
                                 (.getBytes (json/generate-string {:sentence sentence}) "UTF-8"))})]
    (assoc resp :json (json/parse-string (:body resp) true))))

(deftest parses-a-sentence
  (let [{:keys [status json]} (post "John gave Mary the book")]
    (is (= 200 status))
    (testing "the CD frame, with primitives as plain strings for JSON"
      (is (= {:primitive "ATRANS" :actor "John" :from "John" :to "Mary" :object "book"} (:cd json))))
    (testing "tokens carry their lexicon category"
      (is (= {:word "gave" :cat "verb-give"} (second (:tokens json)))))
    (testing "all parses and the step-by-step trace are included"
      (is (= 1 (count (:parses json))))
      (is (some #(re-find #"PARSE COMPLETE" %) (:trace json))))))

(deftest nested-frames-survive-json
  (is (= {:primitive "ATTEND" :actor "Mary" :to {:primitive "INGEST" :actor "dog" :object "bone"}}
         (:cd (:json (post "Mary saw the dog eat the bone"))))))

(deftest unparseable-sentences-return-null-cd
  (let [{:keys [status json]} (post "Mary received a book from John")]
    (is (= 200 status))
    (is (nil? (:cd json)))
    (is (empty? (:parses json)))))

(deftest bad-requests
  (is (= 400 (:status (post "   "))))
  (is (= 413 (:status (post (apply str (repeat 400 "a"))))))
  (is (= 413 (:status (post (clojure.string/join " " (repeat 31 "John")))))))

(deftest localhost-only
  (is (= 403 (:status (post "John ate the apple" :host "evil.example:3100"))))
  (is (= 200 (:status (post "John ate the apple" :host "127.0.0.1:3100")))))
