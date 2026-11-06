(ns atndev.scripts
  "A tiny Schankian *script* + *MOP* layer on top of the CD primitives (atndev.core).

   A SCRIPT is a canned sequence of expected Conceptual Dependency events with
   ROLES (customer, waiter) and PROPS (restaurant, food). Given only the events a
   text states, the script binds its roles and fills the ones left implicit — how
   1970s systems (SAM) answered questions never explicitly written.

   A MOP (Memory Organization Packet) is Schank's later refinement: a *shared*
   sub-sequence factored out of many scripts. Paying is not restaurant-specific —
   you pay in stores and taxis too — so $PAY lives once as a MOP with abstract
   roles (:payer/:payee), and each script points at it with a role mapping. Change
   how paying works in one place and every script that references it updates.

   Flow:  sentences -> cd/parse-cd -> [CD frames] -> instantiate <script>
          -> a full timeline (each step :stated or :inferred, MOP steps tagged)."
  (:require [atndev.core :as cd]
            [atndev.causal :as causal]
            [clojure.string :as str]))

;; ── MOP: $PAY — the shared paying packet ────────────────────────────────────────
;; Situation-independent: abstract :payer/:payee roles + its own props (bill/money).
;; A script supplies {:payer <its-role> :payee <its-role>} to specialise it.
(def pay-mop
  {:name  :$PAY
   :roles #{:payer :payee}
   :props #{:bill :money}
   :defaults {:bill "the bill" :money "the money"}
   :events
   [{:scene :requesting :cd {:primitive :MTRANS :actor :payer :object :bill :to :payee}}
    {:scene :tendering  :cd {:primitive :ATRANS :actor :payer :object :money :from :payer :to :payee}}]})

(def mop-registry {:$PAY pay-mop})

;; ── SCRIPTS ─────────────────────────────────────────────────────────────────────
;; A script event's :cd is a CD template whose role/prop slots are keywords rather
;; than literals. Events with :match/:bind are the observable anchors the parser
;; latches onto; plain events are always inferred; a {:mop ...} entry splices in a
;; MOP's scenes with its abstract roles remapped to this script's roles.
(def restaurant-script
  {:name  :$RESTAURANT
   :goal  :hungry          ; the mental state that drives the whole script (CS5)
   :roles #{:customer :waiter :cook}
   :props #{:restaurant :food :order}
   :defaults {:waiter "the waiter" :cook "the cook"
              :restaurant "the restaurant" :food "the meal" :order "an order"}
   :events
   [{:scene :entering
     :cd    {:primitive :PTRANS :actor :customer :object :customer :to :restaurant}
     :match (fn [f] (and (= :PTRANS (:primitive f)) (= (:actor f) (:object f)) (:to f)))
     :bind  (fn [f] {:customer (:actor f) :restaurant (:to f)})}
    {:scene :ordering
     :cd    {:primitive :MTRANS :actor :customer :object :order :to :waiter}}
    {:scene :serving
     :cd    {:primitive :PTRANS :actor :waiter :object :food :to :customer}}
    {:scene :eating
     :cd    {:primitive :INGEST :actor :customer :object :food}
     :match (fn [f] (= :INGEST (:primitive f)))
     :bind  (fn [f] {:customer (:actor f) :food (:object f)})}
    {:mop :$PAY :roles {:payer :customer :payee :restaurant}}
    {:scene :leaving
     :cd    {:primitive :PTRANS :actor :customer :object :customer :from :restaurant}}]})

;; A second script that reuses the very same $PAY MOP — proving it is shared.
(def store-script
  {:name  :$STORE
   :goal  :wants-goods     ; the mental state that drives the whole script (CS5)
   :roles #{:customer :clerk}
   :props #{:store :goods}
   :defaults {:clerk "the clerk" :store "the store" :goods "the goods"}
   :events
   [{:scene :entering
     :cd    {:primitive :PTRANS :actor :customer :object :customer :to :store}
     :match (fn [f] (and (= :PTRANS (:primitive f)) (= (:actor f) (:object f)) (:to f)))
     :bind  (fn [f] {:customer (:actor f) :store (:to f)})}
    {:scene :selecting
     :cd    {:primitive :GRASP :actor :customer :object :goods}
     :match (fn [f] (= :GRASP (:primitive f)))
     :bind  (fn [f] {:customer (:actor f) :goods (:object f)})}
    {:mop :$PAY :roles {:payer :customer :payee :store}}
    {:scene :leaving
     :cd    {:primitive :PTRANS :actor :customer :object :customer :from :store}}]})

;; ── Engine ───────────────────────────────────────────────────────────────────────

(defn- fill
  "Substitute role/prop keywords in a CD template with bound values (falling back
   to defaults). Literal keywords like the :primitive value pass through untouched."
  [template bindings known defaults]
  (reduce-kv (fn [m k v]
               (assoc m k (if (and (keyword? v) (contains? known v))
                            (or (bindings v) (defaults v) v)
                            v)))
             {} template))

(defn- resolve-events
  "Flatten a script's :events, splicing each {:mop ...} reference into the MOP's
   scenes with its abstract roles remapped to the script's roles. Accumulates the
   MOP's props/defaults so `fill` knows to substitute them too."
  [script mops]
  (reduce
    (fn [acc {:keys [mop] :as entry}]
      (if mop
        (let [m        (get mops mop)
              role-map (:roles entry)
              remap    (fn [v] (get role-map v v))
              evs      (mapv (fn [e]
                               {:scene (:scene e)
                                :mop   mop
                                :cd    (reduce-kv (fn [c k v]
                                                    (assoc c k (if (keyword? v) (remap v) v)))
                                                  {} (:cd e))})
                             (:events m))]
          (-> acc
              (update :events into evs)
              (update :known into (:props m))
              (update :defaults merge (:defaults m))))
        (update acc :events conj entry)))
    {:events [] :known (into (:roles script) (:props script)) :defaults (:defaults script)}
    (:events script)))

(defn bind-roles
  "Scan observed CD frames; wherever a frame matches a script anchor event, learn
   its role bindings (customer / food / store …). MOP refs carry no anchors."
  [script frames]
  (reduce (fn [b f]
            (if-let [ev (and f (some (fn [e] (when (and (:match e) ((:match e) f)) e))
                                     (:events script)))]
              (merge b ((:bind ev) f))
              b))
          {} frames))

(defn instantiate
  "Expand a script (splicing its MOPs) with the roles learned from `frames`. Each
   step is tagged :stated (an observed frame matched it) or :inferred."
  ([script frames] (instantiate script frames mop-registry))
  ([script frames mops]
   (let [{:keys [events known defaults]} (resolve-events script mops)
         bindings (bind-roles script frames)
         stated?  (fn [e] (boolean (and (:match e) (some #(and % ((:match e) %)) frames))))]
     {:script   (:name script)
      :bindings bindings
      :timeline (mapv (fn [e]
                        (cond-> {:scene  (:scene e)
                                 :status (if (stated? e) :stated :inferred)
                                 :cd     (fill (:cd e) bindings known defaults)}
                          (:mop e) (assoc :mop (:mop e))))
                      events)})))

(defn understand
  "Parse a seq of sentences into CD frames, then instantiate a script (default
   $RESTAURANT)."
  ([sentences] (understand restaurant-script sentences))
  ([script sentences] (instantiate script (mapv cd/parse-cd sentences))))

(defn trace-understand
  "Debug view of script + MOP instantiation (cf. core/trace-parse): prints each
   parsed CD frame, which anchor scene it matched and the role bindings it taught,
   any MOPs spliced in, and the resulting timeline (each scene :stated/:inferred).
   Returns the instance, exactly like understand."
  ([sentences] (trace-understand restaurant-script sentences))
  ([script sentences]
   (let [frames (mapv cd/parse-cd sentences)]
     (println "TRACE understand" (:name script))
     (println "1. Parse sentences -> CD frames:")
     (doseq [[s f] (map vector sentences frames)]
       (println (format "     %-34s -> %s" (pr-str s) (pr-str f))))
     (println "2. Bind roles (match stated frames to anchor scenes):")
     (doseq [f frames]
       (if-let [ev (some (fn [e] (when (and (:match e) ((:match e) f)) e)) (:events script))]
         (println (format "     %-40s ⇒ :%s learns %s" (pr-str f) (name (:scene ev)) ((:bind ev) f)))
         (println (format "     %-40s ⇒ (no anchor matched)" (pr-str f)))))
     (let [inst (instantiate script frames)
           mops (distinct (keep :mop (:timeline inst)))]
       (println "   bindings =>" (:bindings inst))
       (when (seq mops)
         (println "3. MOPs spliced:" (str/join ", " (map name mops))
                  "(abstract roles remapped to script roles)"))
       (println "4. Timeline (scene [status] cd):")
       (doseq [{:keys [scene status cd mop]} (:timeline inst)]
         (println (format "     %-11s [%-8s]%s %s"
                          (name scene) (name status)
                          (if mop (str " " (name mop)) "      ")
                          (pr-str cd))))
       inst))))

;; ── Inference + Q&A: the payoff — answer things the text never stated ─────────────

(defn happened?
  "Did `scene` occur in the understood story? Every instantiated scene is asserted
   to have happened, whether stated or inferred."
  [instance scene]
  (boolean (some #(= scene (:scene %)) (:timeline instance))))

(defn paid?
  "Convenience: did the shared $PAY MOP run in this story?"
  [instance]
  (boolean (some #(= :$PAY (:mop %)) (:timeline instance))))

(defn inferences
  "Schank-style entailments the script + MOP + primitives license, even if unstated."
  [{:keys [bindings] :as instance}]
  (let [{:keys [customer food restaurant store]
         :or   {customer "the customer" food "the meal"}} bindings
        place (or restaurant store "the place")]
    (cond-> [(str customer " went to " place "  (PTRANS: entering)")]
      food        (conj (str food " is now inside " customer ", no longer on the plate  (INGEST)")
                        (str customer " was hungry before eating  (INGEST precondition)"))
      (paid? instance) (conj (str customer " paid the bill to " place "  ($PAY MOP: MTRANS + ATRANS)"))
      true        (conj (str customer " is no longer at " place "  (PTRANS: leaving)")))))

(defn explain
  "Human-readable understanding: bound roles, the full timeline with
   [stated]/[inferred] tags (MOP steps flagged), and the licensed inferences."
  [instance]
  (let [line (fn [{:keys [scene status cd mop]}]
               (format "  %-11s [%-8s]%s %s"
                       (name scene) (name status)
                       (if mop (str " " (name mop)) "      ")
                       (pr-str cd)))]
    (str/join "\n"
      (concat [(str "Script " (:script instance) "  bindings=" (:bindings instance))
               "Timeline:"]
              (map line (:timeline instance))
              ["Inferences (never stated, but understood):"]
              (map #(str "  - " %) (inferences instance))))))

;; ── Causal chain: compile the story into typed Schank & Abelson CS1–CS5 links ─────

(defn- bridge-state
  "The STATE that links one scene's act to the next: the act's canonical CS1 result
   (causal/results) when its primitive defines one, else a generic 'scene done' state."
  [{:keys [scene cd]}]
  (or (first (causal/results cd))
      (causal/state (:actor cd) :did (name scene))))

(defn causal-chain
  "Compile an instantiated story into a *validated* Schank & Abelson causal chain:
     • CS5 :reason  — the script's goal (e.g. :hungry) drives the first scene's act
     • CS1 :result  — each scene's act produces a state
     • CS2 :enable  — that state enables the next scene's act
   The result is a vector of causal links you can run through causal/why,
   causal/consequences, and causal/explain — the string `inferences` above,
   upgraded to a typed, traversable causal graph."
  ([instance] (causal-chain instance restaurant-script))
  ([instance script]
   (let [tl       (:timeline instance)
         customer (get-in instance [:bindings :customer])
         goal     (:goal script)]
     (vec
      (concat
       ;; CS5: the goal is the reason for the opening act
       (when (and goal (seq tl))
         [(causal/reason (causal/mental-state customer goal) (:cd (first tl)))])
       ;; CS1 + CS2: thread consecutive scenes through a bridging state
       (mapcat (fn [a b]
                 (let [st (bridge-state a)]
                   [(causal/result (:cd a) st)
                    (causal/enable  st (:cd b))]))
               tl (rest tl)))))))

(defn trace-causal-chain
  "Debug view of compiling a story into a causal chain (cf. core/trace-parse):
   prints the CS5 goal link, then for each adjacent scene pair the bridging state
   and its CS1/CS2 links. Returns the chain, exactly like causal-chain."
  ([instance] (trace-causal-chain instance restaurant-script))
  ([instance script]
   (let [tl       (:timeline instance)
         customer (get-in instance [:bindings :customer])
         goal     (:goal script)]
     (println "TRACE causal-chain" (:name script) " goal =" goal " customer =" (pr-str customer))
     (when (and goal (seq tl))
       (let [ms (causal/mental-state customer goal)]
         (println (format "  CS5 %-24s --reason--> %s"
                          (causal/describe ms) (causal/describe (:cd (first tl)))))))
     (doseq [[a b] (map vector tl (rest tl))]
       (let [st (bridge-state a)]
         (println (format "  %-9s bridge state: %s" (str (name (:scene a)) ":") (causal/describe st)))
         (println (format "    CS1 %-24s --result--> %s" (causal/describe (:cd a)) (causal/describe st)))
         (println (format "    CS2 %-24s --enable--> %s" (causal/describe st) (causal/describe (:cd b))))))
     (let [chain (causal-chain instance script)]
       (println "=>" (count chain) "validated links; valid-chain? =" (causal/valid-chain? chain))
       chain))))

(comment
  ;; (require '[atndev.scripts :as s] :reload)
  (println (explain (understand ["John went to the restaurant" "John ate the lobster"])))
  ;; same $PAY MOP, different script:
  (println (explain (understand store-script ["Mary went to the store" "Mary grabbed the milk"])))
  ;; the story as a typed causal chain (CS1/CS2/CS5), then reason about it:
  (let [story (understand ["John went to the restaurant" "John ate the lobster"])
        chain (causal-chain story)]
    (println (causal/explain chain))
    (println "why leaving? ->" (causal/explain (causal/why chain (:cd (last (:timeline story))))))))
