(ns atndev.server
  "A small JSON API over the CD-ATN parser, for the web UI in ../frontend.

     GET  /api/health                      -> {:status \"ok\"}
     POST /api/parse  {\"sentence\": \"...\"}  -> tokens, the CD frame, every parse, the trace

   Run: clojure -M:dev:server   (listens on 127.0.0.1:3100; SCHANKER_PORT overrides)"
  (:require [atndev.core :as cd]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(def max-sentence-chars 300)
(def max-tokens 30) ; the parser backtracks over every reading; keep inputs sentence-sized

(defn- json-response [status body]
  {:status  status
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body    (json/generate-string body)})

(defn trace-lines
  "The step-by-step agenda trace, captured from cd/trace-parse's printed output so the UI
   shows exactly what the REPL tracer shows (no separate copy of the search loop to drift)."
  [sentence]
  (->> (with-out-str (cd/trace-parse sentence))
       str/split-lines
       (remove str/blank?)
       vec))

(defn parse-result
  "Everything the UI shows for one sentence. :cd is nil when the grammar can't parse it."
  [sentence]
  (let [tokens (cd/tokenize sentence)
        parses (cd/parse-all tokens cd/cd-atn-rules)]
    {:sentence sentence
     :tokens   tokens
     :cd       (first parses)
     :parses   parses
     :trace    (trace-lines sentence)}))

(defn- read-sentence [req]
  (let [body (some-> (:body req) slurp)]
    (try
      (let [s (get (json/parse-string body) "sentence")]
        (when (string? s) (str/trim s)))
      (catch Exception _ nil))))

(defn handler [req]
  (case [(:request-method req) (:uri req)]
    [:get "/api/health"]
    (json-response 200 {:status "ok"})

    [:post "/api/parse"]
    (let [sentence (read-sentence req)]
      (cond
        (str/blank? sentence)
        (json-response 400 {:error "Send JSON like {\"sentence\": \"John gave Mary the book\"}"})

        (> (count sentence) max-sentence-chars)
        (json-response 413 {:error (str "Sentences are limited to " max-sentence-chars " characters")})

        (> (count (cd/tokenize sentence)) max-tokens)
        (json-response 413 {:error (str "Sentences are limited to " max-tokens " words")})

        :else (json-response 200 (parse-result sentence))))

    (json-response 404 {:error "Not found"})))

(def ^:private local-hosts #{"localhost" "127.0.0.1" "[::1]"})

(defn- hostname [host-header]
  (let [h (str/lower-case (or host-header ""))]
    (if (str/starts-with? h "[")
      (subs h 0 (inc (max 0 (str/index-of h "]"))))
      (first (str/split h #":")))))

(defn wrap-localhost-only
  "Reject requests whose Host isn't localhost. The server already listens on 127.0.0.1 only;
   this also stops DNS rebinding, where a web page re-points its own domain at 127.0.0.1."
  [handler]
  (fn [req]
    (if (contains? local-hosts (hostname (get-in req [:headers "host"])))
      (handler req)
      (json-response 403 {:error "Requests must be addressed to localhost"}))))

(def app (wrap-localhost-only handler))

(defonce ^:private server (atom nil))

(defn start!
  ([] (start! (Long/parseLong (or (System/getenv "SCHANKER_PORT") "3100"))))
  ([port]
   (when-let [stop @server] (stop :timeout 100))
   (reset! server (http/run-server #'app {:ip "127.0.0.1" :port port}))
   (println (str "Schanker API on http://127.0.0.1:" port))))

(defn stop! []
  (when-let [stop @server]
    (stop :timeout 100)
    (reset! server nil)))

(defn -main [& _]
  (start!)
  @(promise))
