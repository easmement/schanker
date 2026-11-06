(ns atndev.goal-monitor-test
  "Locks in the Goal Monitor: recognition, interpretation, fate tracking + history,
   prediction; the messy features (origin, specification, substitution, suspension,
   embellishment); THEMEs as a goal source; and story summaries by coordinating goal
   fates. Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [atndev.goal-monitor :as gm]))

(def ad     {:primitive :ATTEND :actor "John" :to "a concert ad"})
(def know   {:primitive :MTRANS :actor "John" :object "the concert" :to "a friend"})
(def go     {:primitive :PTRANS :actor "John" :object "John" :to "the venue"})
(def buy    {:primitive :ATRANS :actor "John" :object "a ticket" :from "box office" :to "John"})

(deftest recognition
  (testing "events trigger the right goals"
    (is (= :SEEK-ENTERTAINMENT (:goal (gm/recognize ad))))
    (is (= :SATISFY-HUNGER (:goal (gm/recognize {:kind :mental-state :actor "Mary" :state :hungry}))))
    (is (nil? (gm/recognize {:primitive :PTRANS :actor "X" :object "X" :to "home"})))))

(deftest interpretation-and-prediction
  (let [mon (gm/observe (gm/monitor) ad)
        g   (first (:active mon))]
    (testing "a recognized goal is interpreted with a plan (its nature)"
      (is (= :SEEK-ENTERTAINMENT (:goal-name g)))
      (is (= :E (:class (gm/nature g))))
      (is (= {:named-plan :attend-event} (:via (gm/nature g)))))
    (testing "prediction lists the pending subgoals in order"
      (is (= [:D-KNOW :D-PROX :D-CONT] (map :subgoal (gm/predict mon)))))))

(deftest fate-tracking-and-history
  (let [mon (gm/watch [ad know go buy])
        g   (first (:active mon))]
    (testing "each expected act advances the fate; the goal ends ACHIEVED"
      (is (= :achieved (:status g)))
      (is (gm/ended? g))
      (is (empty? (gm/predict mon))))
    (testing "the monitor holds the goal's fate history"
      (is (= ["goal recognized" "D-KNOW achieved" "D-PROX achieved"
              "D-CONT achieved" "goal SUCCEEDED"]
             (map :note (:log g))))
      (is (= 1 (count (gm/resolved mon)))))))

(deftest failure-by-obstacle
  (let [mon (gm/watch [ad {:kind :state :actor "John" :obstructs :D-PROX
                           :object "the venue" :state :closed :value true}])
        g   (first (:active mon))]
    (testing "an obstacle blocks a subgoal and the goal ends FAILED"
      (is (= :failed (:status g)))
      (is (= :blocked (get (:outcomes g) :D-PROX))))))

(deftest messy-features
  (let [mon (gm/observe (gm/monitor) ad)]
    (testing "GOAL ORIGIN records what brought the goal into being"
      (is (= :event-driven (:kind (gm/goal-origin (first (:active mon))))))
      (is (= ad (:from (gm/goal-origin (first (:active mon)))))))
    (testing "SPECIFICATION re-plans to a specific realization"
      (let [g (first (:active (gm/specify mon :SEEK-ENTERTAINMENT :via :$CONCERT)))]
        (is (= {:script :$CONCERT} (:via (:plan g))))))
    (testing "SUBSTITUTION swaps the realization and keeps the goal alive"
      (let [g (first (:active (gm/substitute mon :SEEK-ENTERTAINMENT :via :$THEATRE)))]
        (is (= {:script :$THEATRE} (:via (:plan g))))
        (is (nil? (:status g)))))
    (testing "SUSPENSION pauses (drops from predictions), RESUME reactivates"
      (let [susp (gm/suspend mon :SEEK-ENTERTAINMENT "phone rang")]
        (is (gm/suspended? (first (:active susp))))
        (is (empty? (gm/predict susp)))
        (is (nil? (:status (first (:active (gm/resume susp :SEEK-ENTERTAINMENT))))))))
    (testing "EMBELLISHMENT adds instrumental subgoals to the plan"
      (let [g (first (:active (gm/embellish mon :SEEK-ENTERTAINMENT
                                            [{:d-goal :D-KNOW}] :prepend? true)))]
        (is (= {:d-goal :D-KNOW} (first (:steps (:plan g)))))
        (is (= 4 (count (:steps (:plan g)))))))))

(deftest themes-as-goal-source
  (let [mon (gm/activate-theme (gm/monitor) "John" :BON-VIVANT)]
    (testing "a theme seeds the whole package of goals it implies"
      (is (= [:SEEK-ENTERTAINMENT :SATISFY-HUNGER] (map :goal-name (:active mon)))))
    (testing "each themed goal's origin is the theme, not an event"
      (let [g (first (:active mon))]
        (is (= :theme (:origin-kind g)))
        (is (= :BON-VIVANT (:from (gm/goal-origin g))))))))

(deftest role-themes
  (testing "ROLE THEMEs are the societal-role themes"
    (is (= #{:$DOCTOR :$LAWYER :$PSYCHIATRIST :$PRESIDENT} (set (gm/role-themes))))
    (is (= #{:$DOCTOR :$LAWYER :$PSYCHIATRIST :$PRESIDENT} (set (gm/role-themes :societal)))))
  (testing "a role determines the actor's goals (goals come with the position)"
    (let [mon (gm/activate-theme (gm/monitor) "Dr. Who" :$DOCTOR)]
      (is (= [:HELP-CLIENT :EARN-LIVING] (map :goal-name (:active mon))))
      (is (= :theme (:origin-kind (first (:active mon)))))
      (is (= :$DOCTOR (:from (gm/goal-origin (first (:active mon)))))))))

(deftest interpersonal-love
  (testing "LOVE is an interpersonal theme"
    (is (contains? (set (gm/interpersonal-themes)) :LOVE)))
  (let [rel (gm/relationship :LOVE "Romeo" "Juliet" 9)]
    (testing "LOVE is in essence a bundle of predictions about acting toward another"
      (is (= 9 (count (gm/relationship-predictions rel))))
      (is (= {:primitive :ATRANS :actor "Romeo" :object "food" :from "Romeo" :to "Juliet"}
             (gm/predict-toward rel :hungry))))
    (testing "if the loved one is threatened, the lover adopts a CRISIS goal for their health"
      (is (= {:adopt {:goal :PROTECT-OTHER :class :C :actor "Romeo" :toward "Juliet"}}
             (gm/predict-toward rel :threatened))))
    (testing "if a third party harms the loved one, the lover wants to harm the harmer"
      (is (= {:adopt {:goal :HARM :class :A :actor "Romeo" :toward "Tybalt"}}
             (gm/predict-toward rel :harmed "Tybalt"))))
    (testing "a rival who also loves the loved one -> want the rival's love to cease"
      (is (= {:adopt {:goal :THWART-RIVAL :class :A :actor "Romeo" :toward "Paris"}}
             (gm/predict-toward rel :rival "Paris"))))
    (testing "unrequited love -> try to become loved"
      (is (= {:adopt {:goal :BECOME-LOVED :class :A :actor "Romeo" :toward "Juliet"}}
             (gm/predict-toward rel :unrequited)))))
  (testing "the relationship is on a numerical scale — a weak bond licenses fewer predictions"
    (let [weak (gm/relationship :LOVE "Paris" "Juliet" 2)]
      (is (= #{:hungry :sad :apart :unrequited} (set (map :situation (gm/relationship-predictions weak)))))
      (is (nil? (gm/predict-toward weak :in-danger)))          ; needs strength >= 8
      (is (some? (gm/predict-toward (gm/relationship :LOVE "Romeo" "Juliet" 9) :in-danger)))))
  (testing "activate-relationship seeds standing + defeasible goals, tagged :toward/:strength"
    (let [g (first (:active (gm/activate-relationship (gm/monitor) "Romeo" "Juliet" :LOVE :strength 9)))]
      (is (= [:PROTECT-OTHER :PLEASE-OTHER :PROMOTE-OTHER-GOALS :GAIN-APPROVAL :MARRY :MATE]
             (map :goal-name (:active (gm/activate-relationship (gm/monitor) "Romeo" "Juliet" :LOVE)))))
      (is (= "Juliet" (:toward (gm/goal-origin g))))
      (is (= 9 (:strength (gm/goal-origin g))))))
  (testing "defeasible defaults drop out given contrary information (:except)"
    (let [gs (map :goal-name (:active (gm/activate-relationship
                                       (gm/monitor) "Romeo" "Juliet" :LOVE :except #{:MARRY :MATE})))]
      (is (= [:PROTECT-OTHER :PLEASE-OTHER :PROMOTE-OTHER-GOALS :GAIN-APPROVAL] gs)))))

(deftest story-summaries
  (let [mon (gm/watch [ad
                       {:kind :mental-state :actor "Mary" :state :hungry}
                       {:primitive :PTRANS :actor "Mary" :object "Mary" :to "restaurant"}])]
    (testing "all goals (interlocking fate histories)"
      (is (= #{"John" "Mary"} (set (map :actor (gm/summarize mon))))))
    (testing "important goals only keeps the higher-ranked classes (S over E)"
      (is (= [:SATISFY-HUNGER] (map :goal (gm/summarize mon :important? true)))))
    (testing "one character's goals"
      (is (= [:SEEK-ENTERTAINMENT] (map :goal (gm/summarize mon :character "John")))))))
