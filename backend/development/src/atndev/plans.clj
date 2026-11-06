(ns atndev.plans
  "Plans and Goals — the top of the Schank & Abelson hierarchy (Scripts, Plans,
   Goals, and Understanding), sitting above scripts (atndev.scripts) and causal
   links (atndev.causal).

   A PLAN is general knowledge of how actors achieve goals. It connects events that
   a script or plain causal-chain expansion cannot. In a causal chain a plan fills
   the IR gap — the Initiate→Reason bridge: an ACT or STATE *initiates* (CS4) an
   MBUILD, and that MBUILD is the *reason* (CS5) for the next ACT. What is MBUILDed
   is a plan: the reasoning by which the actor decides on actions, each of which then
   drives its own chain of results (CS1) and enablements (CS2).

   The vocabulary, from most abstract to most concrete:
     GOAL       — what an actor wants. Schank's classes: :S satisfaction (hunger),
                  :E enjoyment, :A achievement, :P preservation, :D delta (a subgoal
                  to change one specific state).
     D-GOAL     — a delta goal; organizes the standard methods (PLANBOXES) or scripts
                  that realize a change of state (D-PROX get near, D-CONT get, D-KNOW
                  find out, D-SOC-CONT get someone to act, D-AGENCY get an agent).
     NAMED PLAN — a fixed sequence of instrumental D-goals: the usual path to a goal.
     PLAN       — what is MBUILDed on a given occasion: a goal + the chosen
                  realization (script / planbox / named plan) + the steps it entails."
  (:require [atndev.causal :as causal]
            [clojure.string :as str]))

;; ── D-GOALS: each organizes the standard ways to change one state ────────────────
;; A realization of a D-goal is a PLANBOX (a named method). planbox-acts (below) says
;; how each planbox surfaces as a concrete CD act.
(def delta-goals
  {:D-PROX     {:gloss "be in proximity to a place or object"
                :realizations [{:planbox :walk} {:planbox :drive} {:planbox :take-bus}]}
   :D-CONT     {:gloss "gain control / possession of an object"
                :realizations [{:planbox :buy} {:planbox :ask} {:planbox :bargain} {:planbox :take}]}
   :D-KNOW     {:gloss "come to know a piece of information"
                :realizations [{:planbox :ask} {:planbox :read} {:planbox :observe}]}
   :D-SOC-CONT {:gloss "get another agent to want to do something"
                :realizations [{:planbox :ask} {:planbox :bargain} {:planbox :threaten}]}
   :D-AGENCY   {:gloss "get an agent to act on your behalf"
                :realizations [{:planbox :ask} {:planbox :hire}]}})

(def planbox-acts
  "How each planbox surfaces as a concrete CD act (actor, object) -> CD frame. This
   is where a D-goal's chosen method becomes something the causal layer can chain."
  {:walk     (fn [a o] {:primitive :PTRANS :actor a :object a :to o})
   :drive    (fn [a o] {:primitive :PTRANS :actor a :object a :to o})
   :take-bus (fn [a o] {:primitive :PTRANS :actor a :object a :to o})
   :ask      (fn [a o] {:primitive :MTRANS :actor a :object o :to "someone"})
   :read     (fn [a o] {:primitive :MTRANS :actor "a text" :object o :to a})
   :observe  (fn [a o] {:primitive :ATTEND :actor a :to o})
   :buy      (fn [a o] {:primitive :ATRANS :actor a :object o :from "a seller" :to a})
   :bargain  (fn [a o] {:primitive :ATRANS :actor a :object o :from "a seller" :to a})
   :take     (fn [a o] {:primitive :GRASP  :actor a :object o})
   :threaten (fn [a _o] {:primitive :MTRANS :actor a :object "a threat" :to "someone"})
   :hire     (fn [a _o] {:primitive :ATRANS :actor a :object "money" :from a :to "an agent"})
   :do       (fn [a o]  {:primitive :DO :actor a :object o})})   ; the dummy ACT — "do whatever enables it"

;; ── GOALS (S/E/A/P) and how they are realized — sometimes simply by a script ─────
(def goal-library
  {:SATISFY-HUNGER
   {:class :S :gloss "no longer be hungry"
    :realizations [{:script :$RESTAURANT} {:named-plan :cook-at-home}]}

   ;; The user's example: SEEK-ENTERTAINMENT (an E-goal) has scripts among its
   ;; realizations — $THEATRE / $BASEBALL-GAME / $CONCERT — or a named plan.
   :SEEK-ENTERTAINMENT
   {:class :E :gloss "be entertained"
    :realizations [{:named-plan :attend-event}
                   {:script :$THEATRE} {:script :$BASEBALL-GAME} {:script :$CONCERT}]}

   :ACQUIRE-OBJECT
   {:class :A :gloss "possess a desired object"
    :realizations [{:d-goal :D-CONT}]}

   :STAY-HEALTHY
   {:class :P :gloss "preserve one's health"
    :realizations [{:script :$DOCTOR} {:d-goal :D-KNOW}]}

   ;; Achievement goals that role themes (societal roles) package (see goal-monitor):
   :EARN-LIVING
   {:class :A :gloss "make a living"
    :realizations [{:d-goal :D-CONT}]}
   :HELP-CLIENT
   {:class :A :gloss "serve those the role is responsible for"
    :realizations [{:d-goal :D-SOC-CONT}]}

   ;; Directed goals that interpersonal themes (e.g. LOVE) package (see goal-monitor):
   :PROTECT-OTHER
   {:class :P :gloss "keep the other person safe"
    :realizations [{:d-goal :D-PROX} {:d-goal :D-KNOW}]}
   :PLEASE-OTHER
   {:class :A :gloss "make the other person happy"
    :realizations [{:d-goal :D-CONT}]}
   :HARM
   {:class :A :gloss "do harm to someone"
    :realizations [{:d-goal :D-PROX} {:planbox :threaten}]}

   ;; Standing goals an interpersonal theme (LOVE) packages:
   :PROMOTE-OTHER-GOALS
   {:class :A :gloss "further the other's own goals (by DO — do what enables them)"
    :realizations [{:planbox :do}]}
   :GAIN-APPROVAL
   {:class :A :gloss "win the other's respect and approval" :realizations [{:d-goal :D-SOC-CONT}]}
   :MARRY   {:class :A :gloss "marry the other"           :realizations [{:d-goal :D-SOC-CONT}]}
   :MATE    {:class :A :gloss "have sex with the other"   :realizations [{:d-goal :D-SOC-CONT}]}
   :BECOME-LOVED {:class :A :gloss "be loved by the other" :realizations [{:d-goal :D-SOC-CONT}]}
   :THWART-RIVAL {:class :A :gloss "make a rival's love cease"
                  :realizations [{:planbox :threaten} {:d-goal :D-SOC-CONT}]}})

;; ── NAMED PLANS: a fixed sequence of instrumental D-goals — the usual path ────────
(def named-plans
  {:attend-event {:goal  :SEEK-ENTERTAINMENT
                  :steps [{:d-goal :D-KNOW}    ; find out what's on / where
                          {:d-goal :D-PROX}    ; get to the venue
                          {:d-goal :D-CONT}]}  ; get a ticket
   :dine-out     {:goal  :SATISFY-HUNGER
                  :steps [{:d-goal :D-PROX}    ; get to the restaurant
                          {:d-goal :D-CONT}]}  ; get the food
   :cook-at-home {:goal  :SATISFY-HUNGER
                  :steps [{:d-goal :D-CONT}    ; get ingredients
                          {:d-goal :D-KNOW}    ; know the recipe
                          {:d-goal :D-PROX}]}})

;; ── Lookups ───────────────────────────────────────────────────────────────────────

(defn goal-spec  [goal-name] (or (goal-library goal-name) (delta-goals goal-name)))

(defn realizations
  "The ways to achieve `goal-name` (script / planbox / named-plan / d-goal refs)."
  [goal-name]
  (:realizations (goal-spec goal-name)))

(defn goal
  "A GOAL an actor pursues; :class comes from the library (delta goals are class :D)."
  [actor goal-name & {:keys [object]}]
  {:kind :goal :actor actor :name goal-name :object object
   :class (or (get-in goal-library [goal-name :class])
              (when (delta-goals goal-name) :D))})

;; ── Building a PLAN (the reasoning that gets MBUILDed) ────────────────────────────

(defn- choose-realization
  "Pick a realization for `spec`. With `via`, the one naming it (e.g. :$THEATRE,
   :attend-event, :ask); otherwise the first (the actor's default preference)."
  [spec via]
  (if via
    (or (some (fn [r] (when (some #{via} (vals r)) r)) (:realizations spec))
        (throw (ex-info (str "No realization named " via) {:via via :spec spec})))
    (first (:realizations spec))))

(defn- realization-steps
  "Expand a chosen realization into an ordered vector of step maps. A named plan
   expands to its instrumental D-goals; any other realization is a single step."
  [realization]
  (if-let [np (:named-plan realization)]
    (:steps (named-plans np))
    [realization]))

(defn plan-for
  "MBUILD a PLAN: choose how `actor` will achieve `goal-name` and expand it into
   ordered steps. Options: :via (name a specific realization), :object (what the
   goal is about). Returns a plan map — the content of the MBUILD."
  [actor goal-name & {:keys [via object]}]
  (let [spec (goal-spec goal-name)]
    (when-not spec (throw (ex-info (str "Unknown goal " goal-name) {:goal goal-name})))
    (let [realization (choose-realization spec via)]
      {:kind  :plan
       :actor actor
       :goal  (goal actor goal-name :object object)
       :via   realization
       :steps (realization-steps realization)})))

;; ── Turning a plan's steps into concrete CD acts ──────────────────────────────────

(defn- d-goal-act
  "A D-goal, realized by its default (first) planbox, as a concrete act."
  [actor d-goal-name object]
  (when-let [f (planbox-acts (:planbox (first (:realizations (delta-goals d-goal-name)))))]
    (f actor object)))

(defn- script-setting [script-kw]
  (-> (name script-kw) (str/replace "$" "") str/lower-case))

(defn- step-act
  "Turn one plan step into the CD act that carries it out."
  [actor step object]
  (cond
    (:planbox step) (when-let [f (planbox-acts (:planbox step))] (f actor object))
    (:d-goal step)  (d-goal-act actor (:d-goal step) object)
    ;; a script realization: the plan's first physical move is getting to its setting
    (:script step)  {:primitive :PTRANS :actor actor :object actor :to (script-setting (:script step))}
    :else nil))

(defn plan-actions
  "The concrete CD acts a plan entails — one per step."
  [{:keys [actor steps goal]}]
  (vec (keep #(step-act actor % (:object goal)) steps)))

;; ── Integration with the causal layer: plans fill the IR gap ─────────────────────

(defn plan-thought
  "The MBUILD result: a MENTAL STATE whose content is the plan. (Schank: what is
   MBUILDed is a plan.) It is a valid causal :mental-state node."
  [plan]
  {:kind :mental-state :actor (:actor plan) :state :intends :plan plan})

(defn- bridge-state [cd]
  (or (first (causal/results cd))
      (causal/state (:actor cd) :did (name (:primitive cd)))))

(defn plan->causal
  "Compile a plan into a validated causal chain that fills the IR gap:
     CS4 :initiate — the `initiating-event` (an ACT or STATE) initiates the MBUILD
     CS5 :reason   — the MBUILD (the plan) is the reason for the first action
     CS1/CS2       — the plan's actions then thread result -> enable
   This is precisely the connective tissue a script or plain causal expansion leaves
   out. Feed the result to causal/why, causal/consequences, causal/explain."
  [initiating-event plan]
  (let [thought (plan-thought plan)
        acts    (plan-actions plan)]
    (vec
     (concat
      [(causal/initiate initiating-event thought)]            ; CS4
      (when (seq acts) [(causal/reason thought (first acts))]) ; CS5
      (mapcat (fn [a b]
                (let [s (bridge-state a)]
                  [(causal/result a s) (causal/enable s b)]))
              acts (rest acts))))))                            ; CS1/CS2

;; ── Rendering ──────────────────────────────────────────────────────────────────────

(defn- step-key [step]
  (or (:d-goal step) (:planbox step) (:script step) (:named-plan step)))

(defn- step-name [step]
  (some-> (step-key step) name))

(defn describe-plan
  [{:keys [actor goal via steps]}]
  (format "%s plans to %s [%s] via %s → steps: %s"
          actor (name (:name goal)) (name (:class goal))
          (str/join "/" (map name (vals via)))
          (str/join " → " (map step-name steps))))

(defn explain-plan
  "Full readable view: the plan, then the causal chain it produces (if given the
   event that initiated it)."
  ([plan] (describe-plan plan))
  ([initiating-event plan]
   (str (describe-plan plan) "\n"
        "Causal expansion (IR: initiate → reason, then result/enable):\n"
        (causal/explain (plan->causal initiating-event plan)))))

(defn trace-plan
  "Debug view of planning + the IR bridge (cf. core/trace-parse): prints the goal
   and chosen realization, each step's CD act, then the causal links it produces —
   CS4 initiate (the MBUILD), CS5 reason (first act), and the CS1/CS2 action thread.
   Returns the causal chain, exactly like plan->causal."
  [initiating-event plan]
  (let [thought (plan-thought plan)
        acts    (plan-actions plan)]
    (println "TRACE plan:" (describe-plan plan))
    (println "1. Goal" (name (:name (:goal plan))) (str "[" (name (:class (:goal plan))) "]")
             "→ realization" (pr-str (:via plan)))
    (println "2. Steps → acts:")
    (doseq [[s a] (map vector (:steps plan) acts)]
      (println (format "     %-8s -> %s" (step-name s) (causal/describe a))))
    (println "3. IR bridge (the gap a script / causal-chain can't cross):")
    (println (format "     CS4 %-24s --initiate--> %s"
                     (causal/describe initiating-event) (causal/describe thought)))
    (when (seq acts)
      (println (format "     CS5 %-24s --reason-->   %s"
                       (causal/describe thought) (causal/describe (first acts)))))
    (println "4. Action chain (each act drives result → enable):")
    (doseq [[a b] (map vector acts (rest acts))]
      (let [s (bridge-state a)]
        (println (format "     CS1 %-24s --result--> %s" (causal/describe a) (causal/describe s)))
        (println (format "     CS2 %-24s --enable--> %s" (causal/describe s) (causal/describe b)))))
    (let [chain (plan->causal initiating-event plan)]
      (println "=>" (count chain) "validated links; valid-chain? =" (causal/valid-chain? chain))
      chain)))

;; ── Goal-fate graph: overlay each (sub)goal's outcome on the plan hierarchy ───────
;; A goal's FATE is its outcome (Schank: G-success / G-failure). The graph annotates
;; the goal -> instrumental-D-goal tree with a fate per node:
;;   :achieved  the subgoal succeeded          :blocked    it was thwarted
;;   :abandoned the actor gave it up           :pending    not (yet) attempted
;; The top goal's fate is derived: :failed if any subgoal is blocked/abandoned,
;; :achieved if all subgoals achieved, else :pending.

(defn blocked-at
  "Outcomes for a *linear* plan that stalls at subgoal `blocked-key`: the steps
   before it :achieved, that step :blocked, the steps after it :pending. A handy
   way to describe a scenario like \"got there, but the box office was closed\"."
  [plan blocked-key]
  (let [ks (map step-key (:steps plan))
        [before from] (split-with #(not= % blocked-key) ks)]
    (merge (zipmap before (repeat :achieved))
           (when (seq from) {(first from) :blocked})
           (zipmap (rest from) (repeat :pending)))))

(defn goal-fate-graph
  "Extract a goal-fate graph from a plan. `outcomes` is a map
   {subgoal-key -> :achieved|:blocked|:abandoned}; unlisted subgoals are :pending.
   Returns {:actor :goal :via :fate :subgoals [{:subgoal :act :fate} ...]}."
  ([plan] (goal-fate-graph plan {}))
  ([plan outcomes]
   (let [subs (mapv (fn [step]
                      {:subgoal (step-key step)
                       :act     (step-act (:actor plan) step (:object (:goal plan)))
                       :fate    (get outcomes (step-key step) :pending)})
                    (:steps plan))
         fate (cond
                (some (comp #{:blocked :abandoned} :fate) subs)     :failed
                (and (seq subs) (every? (comp #{:achieved} :fate) subs)) :achieved
                :else :pending)]
     {:actor (:actor plan) :goal (:goal plan) :via (:via plan)
      :fate fate :subgoals subs})))

(def ^:private sub-mark
  {:achieved "✓ achieved" :blocked "✗ blocked" :abandoned "⊘ abandoned" :pending "· pending"})
(def ^:private top-mark
  {:achieved "SUCCESS" :failed "FAILURE" :pending "PENDING"})

(defn fate-diagram
  "Render a goal-fate graph as a textual (ASCII) tree."
  [{:keys [actor goal via fate subgoals]}]
  (let [n (count subgoals)
        rows (map-indexed
              (fn [i {:keys [subgoal act fate]}]
                (format "%s %-9s %-12s — %s"
                        (if (= i (dec n)) "└─" "├─")
                        (name subgoal) (sub-mark fate) (causal/describe act)))
              subgoals)]
    (str/join "\n"
      (cons (format "%s [%s]  (%s, via %s)  ▶ %s"
                    (name (:name goal)) (name (:class goal)) actor
                    (str/join "/" (map name (vals via))) (top-mark fate))
            rows))))

(defn fate-mermaid
  "Render a goal-fate graph as a mermaid `graph TD` (also textual — renders in
   Markdown/artifacts). Node labels carry the fate symbol."
  [{:keys [goal via fate subgoals]}]
  (let [sym {:achieved "✓" :blocked "✗" :abandoned "⊘" :pending "·"}
        root (format "  G[\"%s ▶ %s\"]" (name (:name goal)) (top-mark fate))
        rvia (format "  G --> V([\"via %s\"])" (str/join "/" (map name (vals via))))
        subs (map-indexed
              (fn [i {:keys [subgoal fate act]}]
                (format "  V --> S%d[\"%s %s<br/>%s\"]"
                        i (name subgoal) (sym fate) (causal/describe act)))
              subgoals)]
    (str/join "\n" (concat ["graph TD" root rvia] subs))))

(comment
  ;; (require '[atndev.plans :as p] '[atndev.causal :as c])  — from another ns.
  ;; In THIS ns the fns are unqualified. A gap no single script connects: seeing an
  ;; ad -> asking about tickets. The plan bridges it (the ad initiates an MBUILD
  ;; whose plan is the reason for the acts):
  (let [ad   {:primitive :ATTEND :actor "John" :to "a concert ad"}
        plan (plan-for "John" :SEEK-ENTERTAINMENT :object "the concert")]
    (println (explain-plan ad plan))
    (trace-plan ad plan))
  ;; Same goal, realized directly by a script instead of the named plan:
  (plan-for "John" :SEEK-ENTERTAINMENT :via :$THEATRE)
  ;; A delta goal on its own:
  (plan-for "Mary" :D-CONT :object "the book"))
