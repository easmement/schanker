(ns atndev.scripts-test
  "Locks in the $RESTAURANT script layer: from two stated sentences the script
   binds its roles and fills the unstated scenes — including the shared $PAY MOP
   (requesting + tendering) — so the classic Schankian inferences (a waiter
   served, the customer paid and left) become answerable. Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [atndev.scripts :as s]
            [atndev.causal :as c]))

(def story
  (s/understand ["John went to the restaurant" "John ate the lobster"]))

(deftest binds-roles-from-stated-events
  (testing "customer/restaurant come from PTRANS entering; food from INGEST eating"
    (is (= {:customer "John" :restaurant "restaurant" :food "lobster"}
           (:bindings story)))))

(deftest tags-stated-vs-inferred
  (testing "the two written events are :stated; the rest (incl. the $PAY MOP) inferred"
    (let [status (into {} (map (juxt :scene :status)) (:timeline story))]
      (is (= {:entering :stated  :ordering :inferred :serving :inferred
              :eating   :stated  :requesting :inferred :tendering :inferred
              :leaving  :inferred}
             status)))))

(deftest fills-implicit-scenes
  (testing "the waiter serves the actual food; the $PAY MOP tenders the money"
    (let [by-scene (into {} (map (juxt :scene :cd)) (:timeline story))]
      (is (= {:primitive :PTRANS :actor "the waiter" :object "lobster" :to "John"}
             (:serving by-scene)))
      (is (= {:primitive :ATRANS :actor "John" :object "the money" :from "John" :to "restaurant"}
             (:tendering by-scene))))))

(deftest shared-pay-mop
  (testing "the $PAY MOP is spliced in, its :payer/:payee remapped to script roles"
    (is (s/paid? story))
    (let [pay (filter #(= :$PAY (:mop %)) (:timeline story))]
      (is (= [:requesting :tendering] (mapv :scene pay)))))
  (testing "the very same MOP serves a different script ($STORE) with its own roles"
    (let [shopping (s/understand s/store-script
                                 ["Mary went to the store" "Mary grabbed the milk"])]
      (is (= {:customer "Mary" :store "store" :goods "milk"} (:bindings shopping)))
      (is (s/paid? shopping))
      (is (= {:primitive :ATRANS :actor "Mary" :object "the money" :from "Mary" :to "store"}
             (->> (:timeline shopping) (filter #(= :tendering (:scene %))) first :cd))))))

(deftest answers-unstated-questions
  (testing "every script scene is understood to have happened, stated or not"
    (is (s/happened? story :serving))
    (is (s/happened? story :tendering))
    (is (s/happened? story :leaving))
    (is (s/paid? story))
    (is (not (s/happened? story :dancing)))))

(deftest degrades-with-defaults
  (testing "an empty story still instantiates the frame (7 scenes) using defaults"
    (let [empty-story (s/understand [])]
      (is (= {} (:bindings empty-story)))
      (is (= 7 (count (:timeline empty-story))))
      (is (every? #(= :inferred (:status %)) (:timeline empty-story))))))

(defn- silently
  "Run `f` with stdout discarded, returning its value — for testing trace-* fns."
  [f]
  (binding [*out* (java.io.StringWriter.)] (f)))

(deftest tracers-return-real-results
  (testing "trace-understand returns the same instance understand does"
    (is (= story (silently #(s/trace-understand
                             ["John went to the restaurant" "John ate the lobster"])))))
  (testing "trace-causal-chain returns the same chain causal-chain does"
    (is (= (s/causal-chain story) (silently #(s/trace-causal-chain story))))))

(deftest causal-chain-from-script
  (let [chain (s/causal-chain story)]
    (testing "the story compiles to a well-typed Schank & Abelson causal chain"
      (is (c/valid-chain? chain))
      ;; 7 scenes -> 1 CS5 goal link + 6 adjacent pairs * (CS1 + CS2)
      (is (= 13 (count chain))))
    (testing "the goal (:hungry) is the CS5 reason for the opening PTRANS"
      (let [{:keys [link from to]} (first chain)]
        (is (= :reason link))
        (is (= {:kind :mental-state :actor "John" :state :hungry} from))
        (is (= :PTRANS (:primitive to)))))
    (testing "the chain uses exactly the expected CS types (CS5 once, then CS1/CS2)"
      (is (= (cons :reason (mapcat (constantly [:result :enable]) (range 6)))
             (map :link chain))))
    (testing "causal inference runs over it: everything traces back to the goal"
      (let [leaving (:cd (last (:timeline story)))
            why     (c/why chain leaving)]
        (is (= :reason (:link (first why))))                 ; explanation starts at the goal
        (is (= :hungry (get-in (first why) [:from :state])))))
    (testing "$STORE reuses the same machinery with its own goal"
      (let [shop  (s/understand s/store-script
                                ["Mary went to the store" "Mary grabbed the milk"])
            schain (s/causal-chain shop s/store-script)]
        (is (c/valid-chain? schain))
        (is (= :wants-goods (get-in (first schain) [:from :state])))))))
