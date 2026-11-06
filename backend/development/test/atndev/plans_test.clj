(ns atndev.plans-test
  "Locks in the Plans/Goals layer: goal classes, D-goals + planboxes, named plans,
   plan construction, and — the crux — plans filling the IR (initiate→reason) gap in
   a causal chain. Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [atndev.plans :as p]
            [atndev.causal :as c]))

(deftest goals-and-classes
  (testing "goals carry Schank's class; delta goals are class :D"
    (is (= :E (:class (p/goal "John" :SEEK-ENTERTAINMENT))))
    (is (= :S (:class (p/goal "John" :SATISFY-HUNGER))))
    (is (= :D (:class (p/goal "Mary" :D-CONT)))))
  (testing "a goal's realizations are listed (scripts / named plan for an E-goal)"
    (is (= [{:named-plan :attend-event}
            {:script :$THEATRE} {:script :$BASEBALL-GAME} {:script :$CONCERT}]
           (p/realizations :SEEK-ENTERTAINMENT)))))

(deftest plan-construction
  (testing "a named-plan realization expands to its instrumental D-goal steps"
    (let [plan (p/plan-for "John" :SEEK-ENTERTAINMENT :object "the concert")]
      (is (= :plan (:kind plan)))
      (is (= {:named-plan :attend-event} (:via plan)))
      (is (= [{:d-goal :D-KNOW} {:d-goal :D-PROX} {:d-goal :D-CONT}] (:steps plan)))))
  (testing ":via selects a specific realization — here a script"
    (is (= [{:script :$THEATRE}]
           (:steps (p/plan-for "John" :SEEK-ENTERTAINMENT :via :$THEATRE)))))
  (testing "a delta goal planned on its own uses its default (first) planbox"
    (let [plan (p/plan-for "Mary" :D-CONT :object "the book")]
      (is (= :D (:class (:goal plan))))
      (is (= {:planbox :buy} (:via plan)))))
  (testing "unknown goal / realization are rejected"
    (is (thrown? clojure.lang.ExceptionInfo (p/plan-for "X" :NO-SUCH-GOAL)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (p/plan-for "X" :SEEK-ENTERTAINMENT :via :$NOPE)))))

(deftest plan-actions-are-cd-acts
  (testing "each D-goal step becomes a concrete CD act via its default planbox"
    (let [plan (p/plan-for "John" :SEEK-ENTERTAINMENT :object "the concert")]
      ;; D-KNOW/ask -> MTRANS, D-PROX/walk -> PTRANS, D-CONT/buy -> ATRANS
      (is (= [:MTRANS :PTRANS :ATRANS] (map :primitive (p/plan-actions plan))))))
  (testing "a script realization's first physical act is getting to its setting"
    (is (= [{:primitive :PTRANS :actor "John" :object "John" :to "theatre"}]
           (p/plan-actions (p/plan-for "John" :SEEK-ENTERTAINMENT :via :$THEATRE))))))

(deftest plan-thought-is-a-mental-state
  (testing "what is MBUILDed is a plan, wrapped as a causal mental-state node"
    (let [plan    (p/plan-for "John" :SEEK-ENTERTAINMENT)
          thought (p/plan-thought plan)]
      (is (= :mental-state (c/entity-kind thought)))
      (is (= plan (:plan thought))))))

(deftest plans-fill-the-IR-gap
  (let [ad    {:primitive :ATTEND :actor "John" :to "a concert ad"}
        plan  (p/plan-for "John" :SEEK-ENTERTAINMENT :object "the concert")
        chain (p/plan->causal ad plan)]
    (testing "the chain is well-typed causality"
      (is (c/valid-chain? chain)))
    (testing "CS4: the initiating event initiates the MBUILD (which holds the plan)"
      (let [{:keys [link from to]} (first chain)]
        (is (= :initiate link))
        (is (= ad from))
        (is (= :mental-state (c/entity-kind to)))
        (is (= plan (:plan to)))))
    (testing "CS5: the MBUILD is the reason for the first action (D-KNOW/ask -> MTRANS)"
      (let [{:keys [link to]} (second chain)]
        (is (= :reason link))
        (is (= :MTRANS (:primitive to)))))
    (testing "the actions then thread CS1 result -> CS2 enable"
      (is (= [:initiate :reason :result :enable :result :enable]
             (map :link chain))))
    (testing "causal inference reaches back through the plan to the initiating event"
      (let [go-act (nth (p/plan-actions plan) 1)          ; the PTRANS to the concert
            why    (c/why chain go-act)]
        (is (= :initiate (:link (first why))))            ; explanation bottoms out at CS4
        (is (= ad (:from (first why))))))
    (testing "trace-plan returns exactly the chain plan->causal does"
      (is (= chain (binding [*out* (java.io.StringWriter.)] (p/trace-plan ad plan)))))))

(deftest goal-fate-graphs
  (let [plan (p/plan-for "John" :SEEK-ENTERTAINMENT :object "the concert")]
    (testing "a plan as conceived: every subgoal :pending, goal :pending"
      (let [g (p/goal-fate-graph plan)]
        (is (= :pending (:fate g)))
        (is (= [:D-KNOW :D-PROX :D-CONT] (map :subgoal (:subgoals g))))
        (is (every? #(= :pending (:fate %)) (:subgoals g)))))
    (testing "all subgoals achieved ⇒ G-success"
      (is (= :achieved
             (:fate (p/goal-fate-graph plan {:D-KNOW :achieved :D-PROX :achieved :D-CONT :achieved})))))
    (testing "any blocked subgoal ⇒ G-failure"
      (is (= :failed (:fate (p/goal-fate-graph plan {:D-PROX :blocked})))))
    (testing "blocked-at builds a linear stall: before achieved, at blocked, after pending"
      (is (= {:D-KNOW :achieved :D-PROX :blocked :D-CONT :pending}
             (p/blocked-at plan :D-PROX)))
      (let [g (p/goal-fate-graph plan (p/blocked-at plan :D-PROX))]
        (is (= :failed (:fate g)))
        (is (= [:achieved :blocked :pending] (map :fate (:subgoals g))))))
    (testing "diagrams render as text (ASCII tree + mermaid), returning strings"
      (let [g (p/goal-fate-graph plan (p/blocked-at plan :D-PROX))]
        (is (re-find #"▶ FAILURE" (p/fate-diagram g)))
        (is (re-find #"D-PROX .*✗ blocked" (p/fate-diagram g)))
        (is (str/starts-with? (p/fate-mermaid g) "graph TD"))))))
