(ns atndev.goal-monitor
  "The GOAL MONITOR (Schank & Abelson, Scripts, Plans, Goals, and Understanding):
   an interrelated bundle of processes that
     1. RECOGNIZES when a goal is triggered by an event,
     2. INTERPRETS its nature (goal class + a plan to achieve it),
     3. KEEPS TRACK of the goal's FATE as events arrive — logging every change, and
        when a goal's life ends, holding a history of its fate, and
     4. PREDICTS goal-related events still to come.

   It also handles the 'messy features' of real goal processing:
     • GOAL ORIGIN        — where a goal came from (the triggering event / parent).
     • SPECIFICATION      — refining a vague goal to a specific realization/object.
     • SUBSTITUTION       — swapping in another realization/goal when one won't work.
     • SUSPENSION         — pausing a goal (and resuming it later).
     • EMBELLISHMENT      — adding instrumental subgoals as understanding deepens.

   Several SUMMARIES of a story fall out of coordinating goal fates — the interlocking
   fate histories of all goals, the important goals, or one character's goals. Such
   summarization devices are themselves GOAL-FATE GRAPHS (see atndev.plans)."
  (:require [atndev.plans :as plans]
            [atndev.causal :as causal]
            [clojure.string :as str]))

;; ── 1. RECOGNITION — which goal does an event trigger? ────────────────────────────
(def goal-triggers
  [{:when (fn [e] (and (= :mental-state (causal/entity-kind e)) (= :hungry (:state e))))
    :goal :SATISFY-HUNGER :object (constantly "food")}
   {:when (fn [e] (and (= :ATTEND (:primitive e))
                       (re-find #"(?i)ad|advert|concert|show|game|theatre|theater|movie"
                                (str (:to e)))))
    :goal :SEEK-ENTERTAINMENT :object :to}
   {:when (fn [e] (and (= :mental-state (causal/entity-kind e)) (= :wants (:state e))))
    :goal :ACQUIRE-OBJECT :object :object}])

(defn recognize
  "Does `event` trigger a goal? Returns {:goal :object} or nil."
  [event]
  (some (fn [t] (when ((:when t) event)
                  {:goal (:goal t) :object ((:object t) event)}))
        goal-triggers))

(def goal-importance
  "How pressing a goal class is: preservation/crisis > satisfaction > achievement >
   enjoyment > delta. Used to pick out the 'important' goals of a story."
  {:C 5 :P 5 :S 4 :A 3 :E 2 :D 1})

;; ── log helper (an entry is a note, optionally tied to a CD event) ────────────────
(defn- log-note
  ([g note] (update g :log conj {:note note}))
  ([g note event] (update g :log conj {:note note :event event})))

;; ── 2. INTERPRETATION — build the plan that explains the goal ─────────────────────
(defn- interpret [trigger actor event]
  {:goal-name   (:goal trigger)
   :actor       actor
   :origin      event                 ; GOAL ORIGIN: what brought the goal into being
   :origin-kind :event-driven
   :trigger     event
   :plan        (plans/plan-for actor (:goal trigger) :object (:object trigger))
   :outcomes    {}
   :log         [{:note "goal recognized" :event event}]
   :status      nil})                 ; nil active; :suspended paused; :achieved/:failed/:abandoned ended

(defn nature
  "Interpret an active goal's nature: its class and chosen realization."
  [g]
  {:goal (:goal-name g) :class (:class (:goal (:plan g))) :via (:via (:plan g))})

;; ── THEMEs — where goals ultimately come from ─────────────────────────────────────
;; A THEME is a package of goals that tend to co-occur because of a property of one or
;; more actors — a role, a relationship, or a disposition. Themes are the deepest
;; answer to 'where do goals come from': give an actor a theme and its whole cluster
;; of goals becomes active at once (their GOAL ORIGIN is the theme, not an event).
;;
;; One important kind is the ROLE THEME, in which an actor's goals are determined by a
;; role he occupies. A major category is SOCIETAL roles — president, psychiatrist,
;; doctor, lawyer — each carrying the goals expected of anyone in that position.
(def theme-library
  {;; disposition themes
   :BON-VIVANT {:kind :disposition :property "enjoys the good life"
                :goals [:SEEK-ENTERTAINMENT :SATISFY-HUNGER]}
   :COLLECTOR  {:kind :disposition :property "compulsively acquires things"
                :goals [:ACQUIRE-OBJECT]}
   :HEALTH-NUT {:kind :disposition :property "puts health above all else"
                :goals [:STAY-HEALTHY]}
   ;; ROLE THEMEs — societal roles (goals come with the position)
   :$DOCTOR       {:kind :role :category :societal :property "is a doctor"
                   :goals [:HELP-CLIENT :EARN-LIVING]}
   :$LAWYER       {:kind :role :category :societal :property "is a lawyer"
                   :goals [:HELP-CLIENT :EARN-LIVING]}
   :$PSYCHIATRIST {:kind :role :category :societal :property "is a psychiatrist"
                   :goals [:HELP-CLIENT :EARN-LIVING]}
   :$PRESIDENT    {:kind :role :category :societal :property "is the president"
                   :goals [:HELP-CLIENT]}
   ;; INTERPERSONAL THEMEs — social relationships between two actors. LOVE is not a
   ;; mere stative fact; in essence it is a BUNDLE OF PREDICTIONS about how one person
   ;; will act toward another across situations (plus the goals it packages).
   :LOVE {:kind :interpersonal :property "loves"
          ;; Standing goals of loving someone: protect them, please them, undo their
          ;; sadness / promote their goals (by DO — do what produces the enablements),
          ;; and win their respect and approval.
          :goals [:PROTECT-OTHER :PLEASE-OTHER :PROMOTE-OTHER-GOALS :GAIN-APPROVAL]
          ;; Defeasible defaults — assumed in the absence of contrary information; drop
          ;; them with activate-relationship's :except.
          :default-goals [:MARRY :MATE]
          ;; Each :react fn is (subject loved-one other) -> a reaction: either a CD ACT
          ;; the lover performs, or {:adopt <goal>} — a goal the lover takes on. A
          ;; prediction may require a minimum intensity (:min-strength): only strong
          ;; love braves danger; passing affection still comforts and seeks out.
          :predictions
          [{:situation :in-danger  :min-strength 8 :react (fn [s o _] {:primitive :PTRANS :actor s :object s :to o})}       ; rushes to them
           {:situation :hungry                     :react (fn [s o _] {:primitive :ATRANS :actor s :object "food"    :from s :to o})} ; feeds them
           {:situation :sad                        :react (fn [s o _] {:primitive :MTRANS :actor s :object "comfort"          :to o})} ; comforts them (undo sadness)
           {:situation :special-occasion :min-strength 4 :react (fn [s o _] {:primitive :ATRANS :actor s :object "a gift" :from s :to o})} ; gives a gift
           {:situation :apart                      :react (fn [s o _] {:primitive :PTRANS :actor s :object s :to o})}       ; seeks them out
           ;; if the loved one is threatened, adopt a CRISIS goal (class :C) — preserve
           ;; their health, elevated above ordinary preservation.
           {:situation :threatened :min-strength 5 :react (fn [s o _] {:adopt {:goal :PROTECT-OTHER :class :C :actor s :toward o}})}
           ;; if a third party has harmed the loved one, want to harm the harmer.
           {:situation :harmed     :min-strength 5 :react (fn [s _o harmer] {:adopt {:goal :HARM :class :A :actor s :toward harmer}})}
           ;; a rival who also loves the loved one -> rivalrously want the rival's love to cease.
           {:situation :rival      :min-strength 4 :react (fn [s _o rival] {:adopt {:goal :THWART-RIVAL :class :A :actor s :toward rival}})}
           ;; love unrequited -> try to become loved.
           {:situation :unrequited :react (fn [s o _] {:adopt {:goal :BECOME-LOVED :class :A :actor s :toward o}})}]}
   :FRIENDSHIP {:kind :interpersonal :property "is a friend of"
                :goals [:PLEASE-OTHER]
                :predictions
                [{:situation :in-need :react (fn [s o _] {:primitive :ATRANS :actor s :object "help"      :from s :to o})}
                 {:situation :apart   :react (fn [s o _] {:primitive :MTRANS :actor s :object "a message"         :to o})}]}})

(defn theme-goals
  "The goals a theme packages."
  [theme-name]
  (:goals (theme-library theme-name)))

(defn role-themes
  "The names of the ROLE themes (an actor's goals determined by a role); optionally
   only those in a given :category (e.g. :societal)."
  ([] (role-themes nil))
  ([category]
   (for [[nm t] theme-library
         :when (and (= :role (:kind t)) (or (nil? category) (= category (:category t))))]
     nm)))

(defn interpersonal-themes
  "The names of the INTERPERSONAL themes (relationships between two actors)."
  []
  (for [[nm t] theme-library :when (= :interpersonal (:kind t))] nm))

(defn relationship
  "An emotional relationship: `subject` bears `theme` toward `object` at a numerical
   `strength` on a scale from -10 (enmity) through 0 (indifference) to +10 (intense).
   Defaults to +6."
  ([theme subject object] (relationship theme subject object 6))
  ([theme subject object strength]
   {:kind :relationship :theme theme :subject subject :object object :strength strength}))

(defn relationship-predictions
  "The bundle of predictions a relationship makes — for each situation, how the
   subject reacts toward the object. Each :predict is either a CD ACT the subject
   performs or {:adopt <goal>} — a goal the subject takes on (e.g. a crisis goal to
   protect a threatened loved one, or a goal to harm a harmer). `other` supplies the
   third party a situation may involve (the harmer/threatener). Only predictions the
   bond is strong enough to license (strength >= :min-strength, default 1) are
   included, so a weak relationship predicts less than an intense one. (Schank: LOVE
   *is* in essence such a bundle of predictions.)"
  ([rel] (relationship-predictions rel nil))
  ([{:keys [theme subject object strength]} other]
   (for [{:keys [situation react min-strength] :or {min-strength 1}} (:predictions (theme-library theme))
         :when (>= strength min-strength)]
     {:situation situation :predict (react subject object other)})))

(defn predict-toward
  "How the relationship predicts the subject reacts in `situation` — a CD act or an
   {:adopt <goal>} — with an optional `other` (the third party). nil if the theme
   makes no such prediction or the bond is too weak to license it."
  ([rel situation] (predict-toward rel situation nil))
  ([rel situation other]
   (some (fn [p] (when (= situation (:situation p)) (:predict p)))
         (relationship-predictions rel other))))

(defn activate-relationship
  "Seed the monitor with the goals an INTERPERSONAL theme gives `subject` toward
   `object` at emotional `strength` (default 6). Includes the theme's standing goals
   plus its defeasible :default-goals (e.g. LOVE's marry/mate) unless named in
   `:except` (contrary information). Records who the goals are directed at (:toward)
   and the relationship intensity (:strength)."
  [mon subject object theme-name & {:keys [strength except objects]
                                    :or {strength 6 except #{} objects {}}}]
  (let [t     (theme-library theme-name)
        goals (concat (:goals t) (remove (set except) (:default-goals t)))]
    (reduce (fn [m gn]
              (update m :active conj
                      (-> (interpret {:goal gn :object (or (get objects gn) object)} subject theme-name)
                          (assoc :origin theme-name :origin-kind :theme
                                 :toward object :strength strength
                                 :log [{:note (str "goal from " (name theme-name)
                                                   " (strength " strength ") toward " object)}]))))
            mon goals)))

(defn activate-theme
  "Seed the monitor with the goals a THEME gives `actor` — the ultimate source of
   goals. Each themed goal's origin is the theme (a property of the actor), not an
   event. `objects` optionally supplies a goal->object map."
  [mon actor theme-name & {:keys [objects] :or {objects {}}}]
  (reduce (fn [m gn]
            (update m :active conj
                    (-> (interpret {:goal gn :object (get objects gn)} actor theme-name)
                        (assoc :origin theme-name :origin-kind :theme
                               :log [{:note (str "goal from theme " (name theme-name)
                                                 " (" (:property (theme-library theme-name)) ")")}]))))
          mon (theme-goals theme-name)))

;; ── 3. FATE TRACKING ──────────────────────────────────────────────────────────────
(defn- fate-graph [g] (plans/goal-fate-graph (:plan g) (:outcomes g)))
(defn- top-fate  [g] (:fate (fate-graph g)))

(defn- match-subgoal
  "The first :pending subgoal whose expected act `event` fulfils (same primitive and
   actor)."
  [g event]
  (some (fn [{:keys [subgoal act fate]}]
          (when (and (= :pending fate)
                     (= (:primitive act) (:primitive event))
                     (= (:actor act) (:actor event)))
            subgoal))
        (:subgoals (fate-graph g))))

(defn- update-fate
  "Fold one event into a single goal. A suspended or ended goal is left untouched.
   An :obstructs state blocks the named subgoal; else an event fulfilling a pending
   subgoal's act achieves it. Changes are logged; a change that ends the goal records
   a final fate entry."
  [g event]
  (if (:status g)
    g                                                   ; paused or already ended
    (let [before (top-fate g)
          g' (cond
               (:obstructs event)
               (-> g (assoc-in [:outcomes (:obstructs event)] :blocked)
                     (log-note (str (name (:obstructs event)) " blocked") event))
               :else
               (if-let [sg (match-subgoal g event)]
                 (-> g (assoc-in [:outcomes sg] :achieved)
                       (log-note (str (name sg) " achieved") event))
                 g))
          after (top-fate g')]
      (if (and (not= before after) (#{:achieved :failed} after))
        (-> g' (assoc :status after)
               (log-note (str "goal " (if (= after :achieved) "SUCCEEDED" "FAILED")) event))
        g'))))

;; ── The monitor: state + the observe process ──────────────────────────────────────
(defn monitor [] {:active [] :history []})

(defn observe
  "Feed one event to the monitor: recognize/interpret a new goal it triggers, then
   update the fate of every active goal."
  [mon event & {:keys [actor]}]
  (let [actor   (or actor (:actor event) "someone")
        trig    (recognize event)
        active? (some #(= (:goal-name %) (and trig (:goal trig))) (:active mon))
        mon     (if (and trig (not active?))
                  (update mon :active conj (interpret trig actor event))
                  mon)]
    (-> mon
        (update :active (fn [gs] (mapv #(update-fate % event) gs)))
        (update :history conj event))))

(defn watch [events] (reduce observe (monitor) events))

;; ── 4. PREDICTION ───────────────────────────────────────────────────────────────
(defn predict
  "For each still-active (not suspended/ended) goal, the acts of its pending
   subgoals, soonest first — the events the monitor expects next."
  [mon]
  (for [g (:active mon)
        :when (not (:status g))
        {:keys [subgoal act fate]} (:subgoals (fate-graph g))
        :when (= :pending fate)]
    {:goal (:goal-name g) :subgoal subgoal :expect act}))

;; ── Messy features: transformations on an active goal ─────────────────────────────
(defn- update-goal [mon goal-name f]
  (update mon :active (fn [gs] (mapv #(if (= (:goal-name %) goal-name) (f %) %) gs))))

(defn goal-origin
  "GOAL ORIGIN: what brought a goal into being and how (an event, a theme, or a
   relationship). Includes :toward / :strength when the origin is a relationship."
  [g]
  (cond-> {:goal (:goal-name g) :kind (:origin-kind g) :from (:origin g)}
    (:toward g)   (assoc :toward (:toward g))
    (:strength g) (assoc :strength (:strength g))))

(defn specify
  "GOAL SPECIFICATION: refine an active goal to a more specific realization and/or
   object (e.g. SEEK-ENTERTAINMENT specified via :$CONCERT). Re-plans; keeps history."
  [mon goal-name & {:keys [via object]}]
  (update-goal mon goal-name
    (fn [g]
      (let [obj (or object (:object (:goal (:plan g))))]
        (-> g (assoc :plan (plans/plan-for (:actor g) (:goal-name g) :via via :object obj)
                     :outcomes {})
              (log-note (str "specified" (when via (str " via " (name via)))
                             (when object (str " (object " object ")")))))))))

(defn substitute
  "GOAL SUBSTITUTION: the current approach won't work — swap in another realization
   (:via) or another goal (:goal) for the same end. Re-plans and keeps the goal alive."
  [mon goal-name & {:keys [via goal object]}]
  (update-goal mon goal-name
    (fn [g]
      (let [gn  (or goal (:goal-name g))
            obj (or object (:object (:goal (:plan g))))]
        (-> g (assoc :goal-name gn
                     :plan (plans/plan-for (:actor g) gn :via via :object obj)
                     :outcomes {} :status nil)
              (log-note (str "substituted → " (name gn) (when via (str " via " (name via))))))))))

(defn suspend
  "GOAL SUSPENSION: pause a goal (e.g. to pursue something more urgent). It stops
   accepting fate updates and drops out of predictions until resumed."
  [mon goal-name & [reason]]
  (update-goal mon goal-name
    #(-> % (assoc :status :suspended)
          (log-note (str "suspended" (when reason (str " (" reason ")")))))))

(defn resume
  "Resume a suspended goal."
  [mon goal-name]
  (update-goal mon goal-name
    #(-> % (cond-> (= :suspended (:status %)) (assoc :status nil))
          (log-note "resumed"))))

(defn embellish
  "GOAL EMBELLISHMENT: add instrumental subgoals to a goal's plan as understanding
   deepens. `new-steps` are step maps like [{:d-goal :D-KNOW}]; :prepend? puts them
   first (a newly realized prerequisite)."
  [mon goal-name new-steps & {:keys [prepend?]}]
  (update-goal mon goal-name
    (fn [g]
      (-> g (update-in [:plan :steps]
                       #(vec (if prepend? (concat new-steps %) (concat % new-steps))))
            (log-note (str "embellished +"
                           (str/join "," (map (fn [s] (name (or (:d-goal s) (:planbox s)
                                                                 (:script s)))) new-steps))))))))

;; ── Goal-fate history (output when a goal's life ends) ────────────────────────────
(defn ended?     [g] (boolean (#{:achieved :failed :abandoned} (:status g))))
(defn suspended? [g] (= :suspended (:status g)))
(defn resolved   [mon] (filter ended? (:active mon)))

(defn goal-history
  "The recorded fate history of one goal."
  [g]
  {:goal (:goal-name g) :actor (:actor g) :class (:class (:goal (:plan g)))
   :status (or (:status g) :active) :origin (:origin g) :log (:log g)})

(defn fate-history-text
  "Render one goal's fate history — what the monitor outputs when the goal ends."
  [g]
  (str/join "\n"
    (cons (format "%s's goal %s [%s] — %s"
                  (:actor g) (name (:goal-name g)) (name (:class (:goal (:plan g))))
                  (str/upper-case (name (or (:status g) :active))))
          (map (fn [{:keys [event note]}]
                 (format "   • %-18s %s" note (if event (causal/describe event) "")))
               (:log g)))))

;; ── Story summaries by coordinating goal fates (each summary is a goal-fate graph) ─
(defn summarize
  "A story summary produced by coordinating goal fates — itself a GOAL-FATE GRAPH
   (the interlocking fate histories of a selected set of goals). Options:
     :character <actor>   only that character's goals
     :important? true     only important goals (class rank >= :min-importance)
     :min-importance <n>  importance threshold (default 3 = achievement and up)"
  [mon & {:keys [character important? min-importance] :or {min-importance 3}}]
  (->> (:active mon)
       (filter #(or (nil? character) (= character (:actor %))))
       (filter #(or (not important?)
                    (>= (get goal-importance (:class (:goal (:plan %))) 0) min-importance)))
       (mapv goal-history)))

(defn summary-text
  "One line per goal history: who wanted what, and how it turned out."
  [histories]
  (str/join "\n"
    (map (fn [h] (format "%s wanted %s [%s] → %s"
                         (:actor h) (name (:goal h)) (name (:class h))
                         (str/upper-case (name (:status h)))))
         histories)))

(defn report
  "Full monitor state: each active goal's fate graph, then the predicted next events."
  [mon]
  (str/join "\n\n"
    (concat
     (for [g (:active mon)]
       (str "Goal recognized from: " (causal/describe (:origin g)) "\n"
            (plans/fate-diagram (fate-graph g))))
     [(str "Predicts next: "
           (or (some->> (predict mon) seq
                        (map #(str (name (:subgoal %)) " → " (causal/describe (:expect %))))
                        (str/join "; "))
               "(nothing — all active goals resolved or suspended)"))])))

(defn- fate-summary [g]
  (str/join " " (map (fn [{:keys [subgoal fate]}]
                       (str (name subgoal)
                            ({:achieved "✓" :blocked "✗" :abandoned "⊘" :pending "·"} fate)))
                     (:subgoals (fate-graph g)))))

(defn trace-monitor
  "Run the monitor over `events`, printing each process as it fires, and — when a
   goal's life ends — its fate history. Returns the final monitor."
  [events]
  (println "TRACE goal-monitor")
  (reduce
   (fn [mon e]
     (let [known   (set (map :goal-name (:active mon)))
           endedb4 (set (map :goal-name (resolved mon)))
           mon'    (observe mon e)]
       (println (format "• observe %s" (causal/describe e)))
       (doseq [g (:active mon') :when (not (known (:goal-name g)))]
         (let [{:keys [class via]} (nature g)]
           (println (format "    ▶ RECOGNIZED %s [%s] via %s"
                            (name (:goal-name g)) (name class)
                            (str/join "/" (map name (vals via)))))))
       (doseq [g' (:active mon')
               :let [g (first (filter #(= (:goal-name %) (:goal-name g')) (:active mon)))]
               [k v] (:outcomes g')
               :when (not= v (get (:outcomes g) k))]
         (println (format "    %s %s %s"
                          ({:achieved "✓" :blocked "✗" :abandoned "⊘"} v "·")
                          (name k) (name v))))
       (doseq [g (:active mon')] (println (format "    fate: %s" (fate-summary g))))
       (when-let [ps (seq (predict mon'))]
         (println (format "    predicts: %s"
                          (str/join ", " (map #(str (name (:subgoal %)) " → "
                                                    (causal/describe (:expect %))) ps)))))
       (doseq [g (resolved mon') :when (not (endedb4 (:goal-name g)))]
         (println "    ── goal life ended; fate history ──")
         (println (str/replace (fate-history-text g) #"(?m)^" "      ")))
       mon'))
   (monitor) events))

(comment
  ;; (require '[atndev.goal-monitor :as gm])  — from another ns; here fns are bare.
  (let [mon (watch
             [{:primitive :ATTEND :actor "John" :to "a concert ad"}                 ; John: SEEK-ENTERTAINMENT
              {:kind :mental-state :actor "Mary" :state :hungry}                     ; Mary: SATISFY-HUNGER
              {:primitive :MTRANS :actor "John" :object "the concert" :to "a friend"}; John D-KNOW ✓
              {:primitive :PTRANS :actor "Mary" :object "Mary" :to "restaurant"}     ; Mary succeeds
              {:primitive :PTRANS :actor "John" :object "John" :to "the venue"}      ; John D-PROX ✓
              {:primitive :ATRANS :actor "John" :object "a ticket" :from "box office" :to "John"}])]
    ;; three summaries — each a goal-fate graph over a different slice of the story:
    (println (summary-text (summarize mon)))
    (println (summary-text (summarize mon :important? true)))
    (println (summary-text (summarize mon :character "John"))))
  ;; messy features:
  (-> (observe (monitor) {:primitive :ATTEND :actor "John" :to "a concert ad"})
      (specify :SEEK-ENTERTAINMENT :via :$CONCERT)     ; specification
      (suspend :SEEK-ENTERTAINMENT "phone rang")       ; suspension
      (resume :SEEK-ENTERTAINMENT)                     ; resume
      (substitute :SEEK-ENTERTAINMENT :via :$THEATRE)  ; substitution (concert sold out)
      (embellish :SEEK-ENTERTAINMENT [{:d-goal :D-KNOW}] :prepend? true))) ; embellishment
