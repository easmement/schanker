(ns atndev.core
  (:require [atndev.lexicon :as lexicon]))

;; Visualizing an ATN
;; A sentence like "The cat chased the mouse" is processed by moving through
;; states, jumping into sub-networks (e.g. a Noun Phrase) and returning to the
;; main Sentence network.
;;
;; Step 1: Representing the Network in Clojure — network states, arcs
;; (transitions), and actions as simple maps. Arcs can test, jump to other
;; networks (:push), or set registers.

(def atn-rules
  {;; Noun Phrase Network
   :NP {:start [;; If we see a determiner (e.g., "the"), move to :det state and store it
                {:type :cat :val :det :next :det :action (fn [r word] (assoc r :det word))}
                ;; Or, we can skip the determiner and look for a noun directly
                {:type :cat :val :noun :next :noun :action (fn [r word] (assoc r :noun word))}]
        :det   [{:type :cat :val :noun :next :noun :action (fn [r word] (assoc r :noun word))}]
        :noun  [{:type :pop :action (fn [r] r)}]} ; "Pop" returns the built structure up the stack

   ;; Sentence Network
   :S  {:start [;; To start a sentence, we must recursively parse an NP first
                {:type :push :net :NP :next :got-subject :action (fn [r np-res] (assoc r :subject np-res))}]
        :got-subject [{:type :cat :val :verb :next :got-verb :action (fn [r word] (assoc r :verb word))}]
        :got-verb    [{:type :push :net :NP :next :end :action (fn [r np-res] (assoc r :object np-res))}]
        :end   [{:type :pop :action (fn [r] r)}]}})

;; Step 2: Modeling the parser state — a map with the current node + network, the
;; remaining words, the registers (working memory), and a stack to remember where
;; to return after a recursive :push.
(def initial-parser-state
  {:network   :S
   :node      :start
   :words     '({:word "the" :cat :det}
                {:word "cat" :cat :noun}
                {:word "chased" :cat :verb}
                {:word "the" :cat :det}
                {:word "mouse" :cat :noun})
   :registers {}
   :stack     []})

;; Step 3: The transition engine — look at the current node, find a matching arc,
;; update registers, move to the next state.
(defn step-atn [state rules]
  (let [{:keys [network node words registers stack]} state
        current-word (first words)
        arcs (get-in rules [network node])]

    ;; Look for a valid transition arc
    (some (fn [arc]
            (case (:type arc)
              ;; 1. Category Transition (matches part of speech)
              :cat (when (= (:cat current-word) (:val arc))
                     {:network   network
                      :node      (:next arc)
                      :words     (rest words)
                      :registers ((:action arc) registers (:word current-word))
                      :stack     stack})

              ;; 2. Push Transition (Recursive call)
              :push (let [sub-net (:net arc)]
                      {:network   sub-net
                       :node      :start
                       :words     words
                       :registers {} ; New registers for sub-network
                       ;; Save where to return and what action to take with the result
                       :stack     (conj stack {:return-net network
                                               :return-node (:next arc)
                                               :action (:action arc)
                                               :saved-registers registers})})

              ;; 3. Pop Transition (Return from recursion)
              :pop (when (seq stack)
                     (let [{:keys [return-net return-node action saved-registers]} (last stack)
                           result ((:action arc) registers)] ; evaluate sub-net result
                       {:network   return-net
                        :node      return-node
                        :words     words
                        :registers (action saved-registers result) ; merge result back
                        :stack     (pop stack)}))

              nil))
          arcs)))

;; Step 4: Running the single-path parser — loop until we run out of words and
;; land on :end.
(defn parse [words rules]
  (loop [state {:network :S :node :start :words words :registers {} :stack []}]
    (let [next-state (step-atn state rules)]
      (cond
        (nil? next-state)
        (if (and (empty? (:words state)) (= (:node state) :end))
          (:registers state) ; Success! Return parsed registers.
          (throw (Exception. (str "Parser blocked at state: " (select-keys state [:network :node])))))

        :else (recur next-state)))))

(comment
  ;; Runnable example (readable Clojure forms only):
  (parse '({:word "the" :cat :det}
           {:word "cat" :cat :noun}
           {:word "chased" :cat :verb}
           {:word "the" :cat :det}
           {:word "mouse" :cat :noun}) atn-rules)
  ;; => {:object {:det "the", :noun "mouse"}, :subject {:det "the", :noun "cat"}, :verb "chased"}
  )

;; Backtracking: instead of `some` (first match), return ALL valid next states so
;; a DFS agenda can explore every path.
(defn transition-states
  "Returns a list of all valid next states from the current state."
  [state rules]
  (let [{:keys [network node words registers stack]} state
        current-word (first words)
        arcs (get-in rules [network node])]
    (keep (fn [arc]
            (case (:type arc)
              ;; Category Transition
              :cat (when (= (:cat current-word) (:val arc))
                     {:network   network
                      :node      (:next arc)
                      :words     (rest words)
                      :registers ((:action arc) registers (:word current-word))
                      :stack     stack})

              ;; Push Transition (recursive sub-network). Normally starts the sub-net
              ;; at :start with empty registers; a :start-node and/or :init let a
              ;; caller enter mid-network with seeded registers — used for infinitive
              ;; complements ("decided to leave"), whose subject is the parent's actor.
              :push (let [sub-net (:net arc)]
                      {:network   sub-net
                       :node      (:start-node arc :start)
                       :words     words
                       :registers (if-let [init (:init arc)] (init registers) {})
                       :stack     (conj stack {:return-net network
                                               :return-node (:next arc)
                                               :action (:action arc)
                                               :saved-registers registers})})

              ;; Pop Transition
              :pop (when (seq stack)
                     (let [{:keys [return-net return-node action saved-registers]} (last stack)
                           result ((:action arc) registers)]
                       {:network   return-net
                        :node      return-node
                        :words     words
                        :registers (action saved-registers result)
                        :stack     (pop stack)}))
              nil))
          arcs)))

;; Backtracking loop (DFS): keep an agenda of states to explore; on success record
;; the registers and keep exploring to find ALL valid parses.
(defn parse-all
  "Tries to parse the words, backtracking to find ALL valid parses."
  [words rules]
  (let [initial-state {:network :S :node :start :words words :registers {} :stack []}]
     (loop [agenda       [initial-state] ; Stack of states to explore
           valid-parses []]             ; Collection of successful parses
      (if (empty? agenda)
        valid-parses ; No more paths to explore; return all successful parses
        (let [current-state (peek agenda)
              rest-agenda   (pop agenda)]
          ;; Check if current state is a successful parse. The stack MUST be empty
          ;; too: a recursive :push into :S produces sub-clauses that also end at
          ;; :end, and those must :pop back to their parent (non-empty stack), not
          ;; be mistaken for a finished top-level parse.
          (if (and (empty? (:words current-state))
                   (= (:node current-state) :end)
                   (empty? (:stack current-state)))
            ;; Save successful parse, and continue exploring other branches
            (recur rest-agenda (conj valid-parses (:registers current-state)))
            ;; Otherwise, find all next transitions and push them onto the agenda
            (let [next-branches (transition-states current-state rules)]
              (recur (into rest-agenda next-branches) valid-parses))))))))

;; Schankian Conceptual Dependency network. Instead of building a syntax tree
;; (:subject/:object), the arc actions translate the verb straight into one of
;; Schank's primitive ACTs. Seeing "gave" fires an ATRANS expectation and the
;; parser then goes looking for actor / recipient / object.
(def cd-atn-rules
  {;; Main Sentence Network
   :S {:start [;; a determiner-led subject ("The baby ...") — skip it, stay at :start
               {:type :cat :val :det :next :start :action (fn [r _word] r)}
               {:type :cat :val :noun :next :got-actor
                :action (fn [r word] (assoc r :actor word))}]

       :got-actor [;; "gave" -> ATRANS. The giver is both actor and source, so fill :from.
                   {:type :cat :val :verb-give :next :got-action
                    :action (fn [r _word] (assoc r :primitive :ATRANS :from (:actor r)))}
                   ;; "went/ran/..." -> PTRANS (physical motion). Self-motion, so the
                   ;; object that moves IS the actor.
                   {:type :cat :val :verb-go :next :got-motion
                    :action (fn [r _word] (assoc r :primitive :PTRANS :object (:actor r)))}
                   ;; "told/said/..." -> MTRANS (transfer of mental information). Same
                   ;; ditransitive shape as ATRANS (recipient + object), so it reuses
                   ;; :got-action; only the primitive differs. Teller is the source.
                   {:type :cat :val :verb-tell :next :got-action
                    :action (fn [r _word] (assoc r :primitive :MTRANS :from (:actor r)))}
                   ;; "pushed/kicked/..." -> PROPEL (apply physical force). Transitive:
                   ;; actor - verb - object, no recipient.
                   {:type :cat :val :verb-push :next :got-force
                    :action (fn [r _word] (assoc r :primitive :PROPEL))}
                   ;; INGEST / GRASP / MOVE / EXPEL / SPEAK all share the transitive
                   ;; :got-force object frame (actor - verb - [det] - object); only the
                   ;; primitive differs.
                   {:type :cat :val :verb-ingest :next :got-force
                    :action (fn [r _word] (assoc r :primitive :INGEST))}
                   {:type :cat :val :verb-grasp :next :got-force
                    :action (fn [r _word] (assoc r :primitive :GRASP))}
                   {:type :cat :val :verb-move :next :got-force
                    :action (fn [r _word] (assoc r :primitive :MOVE))}
                   {:type :cat :val :verb-expel :next :got-force
                    :action (fn [r _word] (assoc r :primitive :EXPEL))}
                   {:type :cat :val :verb-speak :next :got-force
                    :action (fn [r _word] (assoc r :primitive :SPEAK))}
                   ;; ATTEND reuses the PTRANS destination frame ("... to/at the X" -> :to).
                   {:type :cat :val :verb-attend :next :got-motion
                    :action (fn [r _word] (assoc r :primitive :ATTEND))}
                   ;; Perception verbs ("saw") take a direct clausal object with no
                   ;; preposition: "Mary saw [the dog eat the bone]" -> ATTEND whose
                   ;; :to is the perceived event. Also ATTEND; differs only in taking
                   ;; the complement directly (:got-percept) instead of via "to"/"at".
                   {:type :cat :val :verb-perceive :next :got-percept
                    :action (fn [r _word] (assoc r :primitive :ATTEND))}
                   ;; Intransitive motion ("Mary left") -> PTRANS with no destination.
                   ;; Mainly earns its keep as an embedded clause ("John said Mary left").
                   {:type :cat :val :verb-depart :next :end
                    :action (fn [r _word] (assoc r :primitive :PTRANS :object (:actor r)))}
                   ;; MBUILD ("decide/conclude"). Intransitive ("Mary decided") ...
                   {:type :cat :val :verb-mbuild :next :end
                    :action (fn [r _word] (assoc r :primitive :MBUILD))}
                   ;; ... OR it builds a nested thought/plan ("John decided to leave",
                   ;; "John said [Mary left]" reuses this via :got-action). The
                   ;; constructed conceptualization is filled in at :got-thought.
                   {:type :cat :val :verb-mbuild :next :got-thought
                    :action (fn [r _word] (assoc r :primitive :MBUILD))}]

       ;; ── ATRANS / MTRANS ditransitive branch: "... Mary the book/story" ──
       :got-action [;; a determiner-led recipient ("... the cat a bone") — skip "the",
                    ;; then take the recipient noun (mirrors the object slot below)
                    {:type :cat :val :det :next :got-recip-det :action (fn [r _word] r)}
                    {:type :cat :val :noun :next :got-recipient
                     :action (fn [r word] (assoc-in r [:to] word))}
                    ;; ... OR a clausal complement — the transferred MENTAL content:
                    ;; "John said [Mary left]" -> :object is the whole nested event,
                    ;; not a flat noun. Recurse into :S (backtracking: a real
                    ;; ditransitive tail like "Mary a story" is not a clause, so only
                    ;; one branch survives).
                    {:type :push :net :S :next :end
                     :action (fn [saved result] (assoc saved :object result))}]

       :got-recip-det [{:type :cat :val :noun :next :got-recipient
                        :action (fn [r word] (assoc-in r [:to] word))}]

       :got-recipient [{:type :cat :val :det :next :got-det
                        ;; skip the determiner ("the") but keep registers intact —
                        ;; every :cat arc MUST have an :action or the engine calls nil
                        :action (fn [r _word] r)}
                       {:type :cat :val :noun :next :end
                        :action (fn [r word] (assoc-in r [:object] word))}]

       :got-det [{:type :cat :val :noun :next :end
                  :action (fn [r word] (assoc-in r [:object] word))}]

       ;; ── MBUILD thought branch: the constructed conceptualization ("... to leave") ──
       :got-thought [;; infinitive complement ("decided to leave"): consume "to", then
                     ;; the bare verb clause whose subject is implied — see :got-inf.
                     {:type :cat :val :prep :next :got-inf :action (fn [r _word] r)}
                     ;; ... OR a full stated clause ("decided that Mary should go"):
                     ;; recurse into :S and store the whole nested CD as :object.
                     {:type :push :net :S :next :end
                      :action (fn [saved result] (assoc saved :object result))}]

       ;; The infinitive has no surface subject, so we enter :S at :got-actor (past
       ;; the subject node) with the actor seeded from the parent — the decider is
       ;; understood to be the one who acts. "John decided to leave" -> the thought
       ;; is {:primitive :PTRANS :actor "John" ...}.
       :got-inf [{:type :push :net :S :start-node :got-actor
                  :init (fn [r] {:actor (:actor r)})
                  :next :end
                  :action (fn [saved result] (assoc saved :object result))}]

       ;; ── PTRANS destination branch: "... to the store" ──
       :got-motion [;; consume the preposition "to" (kept as a register-preserving arc)
                    {:type :cat :val :prep :next :got-to :action (fn [r _word] r)}]

       :got-to [;; optional determiner ("the"), then the destination noun
                {:type :cat :val :det :next :got-dest-det :action (fn [r _word] r)}
                {:type :cat :val :noun :next :end
                 :action (fn [r word] (assoc-in r [:to] word))}
                ;; ... OR an embedded clause with its OWN primitive ("the dog eat
                ;; the bone"): recurse into the whole :S network, so :to becomes a
                ;; full nested CD like {:primitive :INGEST :actor "dog" :object "bone"}.
                {:type :push :net :S :next :end
                 :action (fn [saved result] (assoc saved :to result))}
                ;; ... OR a bare intransitive clause ("the dog snore"): :S has no
                ;; arc for a :verb-intrans, so this simpler subject+verb sub-net
                ;; catches it. A verb is tagged either primitive (→ :S) or
                ;; :verb-intrans (→ :CLAUSE), never both, so at most one succeeds.
                {:type :push :net :CLAUSE :next :end
                 :action (fn [saved result] (assoc saved :to result))}]

       :got-dest-det [{:type :cat :val :noun :next :end
                       :action (fn [r word] (assoc-in r [:to] word))}]

       ;; ── Perception complement branch: "saw [the dog eat the bone]" (no prep) ──
       :got-percept [;; a plain-noun object ("Mary saw the dog") — like a destination
                     {:type :cat :val :det :next :got-percept-det :action (fn [r _word] r)}
                     {:type :cat :val :noun :next :end
                      :action (fn [r word] (assoc-in r [:to] word))}
                     ;; ... OR the perceived event as a full nested CD (recurse into :S)
                     {:type :push :net :S :next :end
                      :action (fn [saved result] (assoc saved :to result))}]

       :got-percept-det [{:type :cat :val :noun :next :end
                          :action (fn [r word] (assoc-in r [:to] word))}]

       ;; ── Transitive object branch (PROPEL/INGEST/GRASP/MOVE/EXPEL/SPEAK): "... the cart" ──
       :got-force [;; skip a verb particle ("spit up", "picked up") and stay put
                   {:type :cat :val :particle :next :got-force :action (fn [r _word] r)}
                   ;; optional determiner ("the"/"her"), then the object acted on
                   {:type :cat :val :det :next :got-force-det :action (fn [r _word] r)}
                   {:type :cat :val :noun :next :end
                    :action (fn [r word] (assoc-in r [:object] word))}]

       :got-force-det [{:type :cat :val :noun :next :end
                        :action (fn [r word] (assoc-in r [:object] word))}]

       :end [{:type :pop :action (fn [r] r)}]}

   ;; ── Embedded clause sub-network (Schankian sentential complement) ──
   ;; In CD, ATTEND's object can itself be a conceptualization: "Mary listened to
   ;; [the dog snore]" is ATTEND whose :to is the whole event {dog snores}, not a
   ;; plain noun. The :push arc from :got-to enters here, parses "det? subject verb"
   ;; into its own registers, and :pop hands that nested CD back to the parent.
   :CLAUSE {:start [{:type :cat :val :det :next :cl-det
                     :action (fn [r _word] r)}
                    {:type :cat :val :noun :next :cl-subj
                     :action (fn [r word] (assoc r :actor word))}]
            :cl-det [{:type :cat :val :noun :next :cl-subj
                      :action (fn [r word] (assoc r :actor word))}]
            :cl-subj [{:type :cat :val :verb-intrans :next :cl-end
                       :action (fn [r word] (assoc r :action word))}]
            :cl-end [{:type :pop :action (fn [r] r)}]}})

;; Example CD outputs (all 11 primitives; MOVE/INGEST/EXPEL/GRASP/SPEAK share the
;; transitive object frame, ATTEND shares the destination frame, MBUILD is intransitive):
;;   "John gave Mary the book"    -> {:primitive :ATRANS :actor "John" :object "book"  :from "John" :to "Mary"}
;;   "John went to the store"     -> {:primitive :PTRANS :actor "John" :object "John"  :to "store"}
;;   "John told Mary a story"     -> {:primitive :MTRANS :actor "John" :object "story" :from "John" :to "Mary"}
;;   "John pushed the cart"       -> {:primitive :PROPEL :actor "John" :object "cart"}
;;   "Mary waved her hand"        -> {:primitive :MOVE   :actor "Mary" :object "hand"}
;;   "John ate the apple"         -> {:primitive :INGEST :actor "John" :object "apple"}
;;   "The baby spit up the milk"  -> {:primitive :EXPEL  :actor "baby" :object "milk"}
;;   "Mary grabbed the hammer"    -> {:primitive :GRASP  :actor "Mary" :object "hammer"}
;;   "John yelled fire"           -> {:primitive :SPEAK  :actor "John" :object "fire"}
;;   "Mary listened to the music" -> {:primitive :ATTEND :actor "Mary" :to "music"}
;;   "Mary decided"               -> {:primitive :MBUILD :actor "Mary"}
;; Embedded clause (ATTEND's object is itself a conceptualization, via :push/:pop):
;;   "Mary listened to the dog snore"     ; bare intransitive -> :CLAUSE sub-net
;;     -> {:primitive :ATTEND :actor "Mary" :to {:actor "dog" :action "snore"}}
;;   "Mary saw the dog eat the bone"      ; recurses into :S -> nested CD with its
;;                                        ; own primitive (perception verb, no prep)
;;     -> {:primitive :ATTEND :actor "Mary"
;;         :to {:primitive :INGEST :actor "dog" :object "bone"}}
;;   "John said Mary left"                ; MTRANS transfers a whole conceptualization
;;     -> {:primitive :MTRANS :actor "John" :from "John"
;;         :object {:primitive :PTRANS :actor "Mary" :object "Mary"}}
;;   "John decided to leave"              ; MBUILD's constructed thought (infinitive:
;;                                        ; implied subject = the decider)
;;     -> {:primitive :MBUILD :actor "John"
;;         :object {:primitive :PTRANS :actor "John" :object "John"}}

;; ── Tokenizer + driver: string -> tokens -> parse-all -> CD structure ───────────
;; The word -> ATN category lexicon now lives in atndev.lexicon (grouped by category
;; and easy to grow; unknown words still default to :noun).

(defn tokenize
  "\"John gave Mary the book\" -> [{:word \"John\" :cat :noun} ...]. Looks each word
   up in atndev.lexicon/lexicon (lowercased); unknown words default to :noun."
  [sentence]
  (->> (.split (.trim ^String sentence) "\\s+")
       (remove empty?)
       (mapv (fn [w] {:word w :cat (get lexicon/lexicon (.toLowerCase ^String w) :noun)}))))

(defn parse-cd
  "Tokenize the sentence, run it through the CD ATN with parse-all (which does the
   backtracking DFS via transition-states + cd-atn-rules), and return the first
   Schankian Conceptual Dependency structure. nil if it doesn't parse."
  [sentence]
  (first (parse-all (tokenize sentence) cd-atn-rules)))

(defn trace-parse
  "Debug view of the DFS: reruns the parse-all agenda loop but prints every step —
   which (network/node) state is popped off the agenda, the words still to consume,
   the registers accumulated so far, the push/pop stack depth, and how many next
   branches the state fans out into. Returns the same vector parse-all would.
   Usage: (trace-parse \"John gave Mary the book\")."
  ([sentence] (trace-parse sentence cd-atn-rules))
  ([sentence rules]
   (let [words   (tokenize sentence)
         initial {:network :S :node :start :words words :registers {} :stack []}
         wds     (fn [ws] (mapv :word ws))]
     (println "TRACE" (pr-str sentence) "tokens=" (wds words))
     (loop [agenda [initial] step 1 parses []]
       (if (empty? agenda)
         (do (println "DONE —" (count parses) "parse(s):")
             (doseq [p parses] (println "  =>" (pr-str p)))
             parses)
         (let [s           (peek agenda)
               rest-agenda (pop agenda)
               {:keys [network node words registers stack]} s]
           ;; success iff at :end, no words left, AND stack empty (mirrors parse-all:
           ;; a sub-clause at :end with a non-empty stack must :pop, not finish)
           (if (and (empty? words) (= node :end) (empty? stack))
             (do (println (format "%3d  DONE  %s/%s  regs=%s  ✓ PARSE COMPLETE"
                                   step network node (pr-str registers)))
                 (recur rest-agenda (inc step) (conj parses registers)))
             (let [nexts (transition-states s rules)]
               (println (format "%3d  visit %s/%s  words=%s  regs=%s  depth=%d  → %d branch(es)"
                                 step network node (wds words) (pr-str registers)
                                 (count stack) (count nexts)))
               (recur (into rest-agenda nexts) (inc step) parses)))))))))

;; ── The 11 Conceptual Dependency primitive ACTs (reference) ─────────────────────
;; A parsed sentence's :primitive is one of these. (Kept as line comments: the
;; reader would choke on a (comment ...) body containing tokens like "MBUILD:".)
;;   ATRANS  - transfer of an abstract relationship (possession/ownership): give, buy
;;   PTRANS  - transfer of physical location: go, run, fall
;;   MTRANS  - transfer of mental information: tell, realize
;;   MBUILD  - build new information from old: decide, conclude
;;   PROPEL  - apply physical force: push, pull, kick
;;   MOVE    - move a body part: nod, kick a leg
;;   GRASP   - grip an object: hold, clutch
;;   INGEST  - take a substance into the body: eat, drink
;;   EXPEL   - force something out of the body: cry, sweat
;;   SPEAK   - produce sound/vocalization: say, sing
;;   ATTEND  - direct a sense organ at a stimulus: listen, look

(comment
  (require 'atndev.core)
  (atndev.core/parse-cd "John gave Mary the book")
  ;;{:actor "John", :from "John", :object "book", :primitive :ATRANS, :to "Mary"}
  )