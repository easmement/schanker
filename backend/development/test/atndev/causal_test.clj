(ns atndev.causal-test
  "Locks in the five Schank & Abelson causal link types (CS1–CS5): their node-kind
   typing, the CS1 generative layer, and traversal-based causal inference (why /
   consequences). Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [atndev.causal :as c]
            [atndev.core :as cd]))

;; shared nodes
(def hungry       (c/mental-state "John" :hungry))
(def go-rest      (cd/parse-cd "John went to the restaurant"))
(def at-rest      (c/state "John" :location "restaurant"))
(def eat          (cd/parse-cd "John ate the food"))
(def food-in      (c/state "food" :location "inside John"))

(deftest node-kinds
  (testing "the three causal node kinds are discriminated correctly"
    (is (= :act          (c/entity-kind eat)))       ; a CD frame (has :primitive)
    (is (= :state        (c/entity-kind at-rest)))
    (is (= :mental-state (c/entity-kind hungry)))
    (is (nil?            (c/entity-kind {:foo 1})))))

(deftest cs-types-accept-correct-kinds
  (testing "CS1 result: ACT -> STATE"
    (is (= {:link :result :from eat :to food-in} (c/result eat food-in))))
  (testing "CS2 enable: STATE -> ACT"
    (is (= {:link :enable :from at-rest :to eat} (c/enable at-rest eat))))
  (testing "CS3 disable: STATE -> ACT"
    (is (= :disable (:link (c/disable (c/state "restaurant" :status :closed) eat)))))
  (testing "CS4 initiate: STATE or ACT -> MENTAL STATE"
    (is (= :initiate (:link (c/initiate go-rest (c/mental-state "John" :tired)))))
    (is (= :initiate (:link (c/initiate at-rest (c/mental-state "John" :calm))))))
  (testing "CS5 reason: MENTAL STATE -> ACT"
    (is (= {:link :reason :from hungry :to go-rest} (c/reason hungry go-rest)))))

(deftest cs-types-reject-wrong-kinds
  (testing "the causal grammar is enforced — ill-typed links throw"
    (is (thrown? clojure.lang.ExceptionInfo (c/result at-rest food-in))) ; CS1 needs ACT antecedent
    (is (thrown? clojure.lang.ExceptionInfo (c/enable eat eat)))         ; CS2 needs STATE antecedent
    (is (thrown? clojure.lang.ExceptionInfo (c/reason at-rest eat)))     ; CS5 needs MENTAL antecedent
    (is (thrown? clojure.lang.ExceptionInfo (c/initiate hungry eat)))    ; CS4 consequent must be MENTAL
    (is (thrown? clojure.lang.ExceptionInfo (c/causal-link :bogus eat food-in)))))

(deftest cs1-generative-from-parsed-acts
  (testing "results/result-links derive CS1 states from a CD primitive"
    (is (= [(c/state "John" :location "restaurant")]
           (c/results go-rest)))                                          ; PTRANS -> location
    (is (= [(c/state "book" :possession "Mary")
            (c/state "book" :possession-lost "John")]
           (c/results (cd/parse-cd "John gave Mary the book"))))          ; ATRANS -> gained + lost
    (testing "result-links wraps them as validated CS1 links"
      (let [links (c/result-links go-rest)]
        (is (every? #(= :result (:link %)) links))
        (is (c/valid-chain? links))))))

(deftest inference-traversal
  (let [chain [(c/reason hungry go-rest)   ; CS5
               (c/result go-rest at-rest)  ; CS1
               (c/enable at-rest eat)]]    ; CS2
    (testing "valid-chain? accepts a well-typed chain"
      (is (c/valid-chain? chain)))
    (testing "forward: consequences of the hunger drive the whole chain in order"
      (is (= chain (c/consequences chain hungry))))
    (testing "backward: why did John eat? — the reason/result/enable path, antecedent-first"
      (is (= chain (c/why chain eat))))
    (testing "a node with no outgoing links has no consequences"
      (is (= [] (c/consequences chain eat))))
    (testing "trace-consequences / trace-why return exactly what the plain fns do"
      (binding [*out* (java.io.StringWriter.)]
        (is (= (c/consequences chain hungry) (c/trace-consequences chain hungry)))
        (is (= (c/why chain eat)             (c/trace-why chain eat)))))))
