(ns atndev.causal
  "Schank & Abelson causal links — the 'syntax of causality' from *Scripts, Plans,
   Goals, and Understanding* (1977) — layered on the Conceptual Dependency parser
   (atndev.core). Where a CD frame captures the meaning of one clause, causal links
   connect frames into the chains that let a reader infer *why* things happen.

   Three kinds of node take part:
     • ACT           — a CD conceptualization (has a :primitive), from cd/parse-cd
     • STATE         — a world condition: an object in some state with a value
     • MENTAL STATE  — a belief / desire / emotion held by an actor

   Five causal link types, each constrained to specific node kinds (that typing is
   what makes causal inference sound):
     CS1 :result   ACT            -> STATE          actions cause state changes
     CS2 :enable   STATE          -> ACT            states enable actions
     CS3 :disable  STATE          -> ACT            states disable actions
     CS4 :initiate STATE or ACT   -> MENTAL STATE   the world initiates thoughts
     CS5 :reason   MENTAL STATE   -> ACT            thoughts are reasons for actions

   The classic loop is CS5→CS1→CS2 (a want drives an act, which changes the world,
   which enables the next act) with CS4→CS5 feeding new wants in."
  (:require [clojure.string :as str]))

;; ── Node constructors + kind discrimination ─────────────────────────────────────

(defn state
  "A world STATE: `object` is in state `state-kw` with `value`.
   Schank notation (OBJECT (STATE VAL)), e.g. (door (STATE OPEN))."
  [object state-kw value]
  {:kind :state :object object :state state-kw :value value})

(defn mental-state
  "A MENTAL STATE (belief / desire / emotion) held by `actor`, e.g. :hungry."
  [actor ms-kw]
  {:kind :mental-state :actor actor :state ms-kw})

(defn entity-kind
  "Which of the three causal node kinds is `x`? :act (a CD conceptualization),
   :state, :mental-state, or nil."
  [x]
  (cond
    (:primitive x)              :act          ; a CD frame from cd/parse-cd
    (= :state (:kind x))        :state
    (= :mental-state (:kind x)) :mental-state
    :else                       nil))

;; ── The five causal link types ──────────────────────────────────────────────────

(def causal-types
  "Each link type declares the node kinds it may connect. Antecedent kind must be in
   :from, consequent kind in :to — this is the causal grammar being enforced."
  {:result   {:cs "CS1" :from #{:act}          :to #{:mental-state :state} :gloss "an action results in a state change"}
   :enable   {:cs "CS2" :from #{:state}        :to #{:act}                 :gloss "a state enables an action"}
   :disable  {:cs "CS3" :from #{:state}        :to #{:act}                 :gloss "a state disables an action"}
   :initiate {:cs "CS4" :from #{:state :act}   :to #{:mental-state}        :gloss "a state or act initiates a mental state"}
   :reason   {:cs "CS5" :from #{:mental-state} :to #{:act}                 :gloss "a mental state is the reason for an action"}})

(defn causal-link
  "Build a validated causal link. Throws if `from`/`to` are not the node kinds the
   `link-type` permits — you cannot, e.g., :enable an action from another action."
  [link-type from to]
  (let [spec (causal-types link-type)]
    (when-not spec
      (throw (ex-info (str "Unknown causal link type: " link-type)
                      {:link-type link-type :known (keys causal-types)})))
    (let [{:keys [cs] from-kinds :from to-kinds :to} spec
          fk (entity-kind from) tk (entity-kind to)]
      (when-not (contains? from-kinds fk)
        (throw (ex-info (str cs " (" (name link-type) ") antecedent must be "
                             from-kinds ", got " fk)
                        {:link-type link-type :from from :from-kind fk})))
      (when-not (contains? to-kinds tk)
        (throw (ex-info (str cs " (" (name link-type) ") consequent must be "
                             to-kinds ", got " tk)
                        {:link-type link-type :to to :to-kind tk})))
      {:link link-type :from from :to to})))

;; convenience constructors
(defn result   [act state']     (causal-link :result   act state'))
(defn enable   [state' act]     (causal-link :enable   state' act))
(defn disable  [state' act]     (causal-link :disable  state' act))
(defn initiate [cause mental]   (causal-link :initiate cause mental))
(defn reason   [mental act]     (causal-link :reason   mental act))

;; ── CS1 made generative: canonical state changes per CD primitive ────────────────

(defn results
  "CS1: the STATE change(s) an ACT canonically produces, keyed on its primitive.
   This is how a parsed act (cd/parse-cd) feeds the causal chain automatically."
  [act]
  (case (:primitive act)
    :PTRANS [(state (:object act) :location (:to act))]
    :ATRANS [(state (:object act) :possession (:to act))
             (state (:object act) :possession-lost (:from act))]
    :MTRANS [(state (:to act) :knows (:object act))]        ; recipient now holds the info
    :GRASP  [(state (:object act) :possession (:actor act))]
    :INGEST [(state (:object act) :location (str "inside " (:actor act)))
             (state (:actor act)  :hunger :sated)]
    :PROPEL [(state (:object act) :location :displaced)]
    []))

(defn result-links
  "Wrap `results` as validated CS1 links from the act to each state it produces."
  [act]
  (mapv #(result act %) (results act)))

;; ── Traversal-based causal inference ──────────────────────────────────────────────

(defn valid-chain?
  "A chain is a vector of links. True if every entry is a well-formed causal link
   for a known type connecting the kinds that type allows."
  [chain]
  (every? (fn [{:keys [link from to]}]
            (when-let [spec (causal-types link)]
              (and (contains? (:from spec) (entity-kind from))
                   (contains? (:to spec)   (entity-kind to)))))
          chain))

(defn- outgoing [chain node] (filter #(= (:from %) node) chain))
(defn- incoming [chain node] (filter #(= (:to %)   node) chain))

(defn consequences
  "Forward transitive closure: the links reached by following causality out of
   `node` — everything it causally leads to, in order encountered."
  [chain node]
  (loop [frontier [node] seen #{} acc []]
    (if (empty? frontier)
      acc
      (let [n     (first frontier)
            outs  (remove (set acc) (outgoing chain n))
            nexts (remove seen (map :to outs))]
        (recur (into (subvec (vec frontier) 1) nexts)
               (into (conj seen n) nexts)
               (into acc outs))))))

(defn causes
  "Backward closure: the links that causally led to `node`, ordered antecedent-first
   so the sequence reads as an explanation."
  [chain node]
  (loop [frontier [node] seen #{} acc []]
    (if (empty? frontier)
      (vec (reverse acc))
      (let [n     (first frontier)
            ins   (remove (set acc) (incoming chain n))
            prevs (remove seen (map :from ins))]
        (recur (into (subvec (vec frontier) 1) prevs)
               (into (conj seen n) prevs)
               (into acc ins))))))

;; ── Rendering / explanation ───────────────────────────────────────────────────────

(defn describe
  "Short human-readable form of a causal node."
  [x]
  (case (entity-kind x)
    :act          (str/join " " (remove nil? [(:actor x) (name (:primitive x)) (:object x)
                                              (when (:to x) (str "→" (:to x)))]))
    :state        (str (:object x) "{" (name (:state x)) "=" (if (keyword? (:value x))
                                                               (name (:value x)) (:value x)) "}")
    :mental-state (str (:actor x) "{" (name (:state x)) "}")
    (pr-str x)))

(defn explain-link
  "One causal step as text: antecedent --CSn:type[gloss]--> consequent."
  [{:keys [link from to]}]
  (let [{:keys [cs gloss]} (causal-types link)]
    (format "%-28s --%s %-8s--> %-24s  (%s)"
            (describe from) cs (name link) (describe to) gloss)))

(defn explain
  "Render a whole chain (or list of links) line by line."
  [links]
  (str/join "\n" (map #(str "  " (explain-link %)) links)))

(defn why
  "Explain `node`: the causal links that led to it, antecedent-first."
  [chain node]
  (causes chain node))

;; ── Traced inference: watch the causal graph being walked (cf. core/trace-parse) ──

(defn- trace-closure
  "Shared engine for trace-consequences / trace-why. Walks the causal graph from
   `start` (:forward via :to, :backward via :from), printing one line per node
   visited — the node, how many causal links lead out of it (the fan-out), and
   where they go. Returns exactly what consequences/causes would."
  [chain start dir]
  (let [nbrs (if (= dir :forward) #(filter (fn [l] (= (:from l) %2)) %1)
                                  #(filter (fn [l] (= (:to l)   %2)) %1))
        nxt  (if (= dir :forward) :to :from)]
    (println "TRACE" (name dir) "from" (describe start))
    (loop [frontier [start] seen #{} acc [] step 1]
      (if (empty? frontier)
        (do (println "DONE —" (count acc) "causal link(s)")
            (if (= dir :backward) (vec (reverse acc)) acc))
        (let [n     (first frontier)
              links (remove (set acc) (nbrs chain n))
              nexts (remove seen (map nxt links))]
          (println (format "%3d  %-26s  %d link(s)%s"
                           step (describe n) (count links)
                           (if (seq links)
                             (str " → " (str/join ", "
                                          (map #(str (:cs (causal-types (:link %)))
                                                     ":" (describe (nxt %))) links)))
                             "")))
          (recur (into (vec (rest frontier)) nexts)
                 (into (conj seen n) nexts)
                 (into acc links)
                 (inc step)))))))

(defn trace-consequences
  "Like consequences, but prints the forward walk step by step. Returns the links."
  [chain node]
  (trace-closure chain node :forward))

(defn trace-why
  "Like why, but prints the backward walk step by step. Returns the links."
  [chain node]
  (trace-closure chain node :backward))
