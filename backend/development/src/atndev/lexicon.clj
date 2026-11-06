(ns atndev.lexicon
  "The CD parser's word→category lexicon, factored out of atndev.core so it can grow
   on its own. Words are grouped by category (easy to read and extend); `lexicon` is
   the flat, lowercased word→category map the tokenizer consumes. Unknown words
   default to :noun in the parser, so you only list the *function* words and verbs.

   To add words:
     • edit the vectors in `word-groups` below, or
     • at the REPL / from code: (def lexicon (add-words lexicon :verb-give [\"gifted\"])), or
     • from data: put {:verb-give [\"gifted\"] …} in an EDN file and
       (def lexicon (from-groups (merge-groups word-groups (load-groups \"my-words.edn\"))))

   The category keys MUST match the arcs in atndev.core/cd-atn-rules
   (:verb-give → ATRANS, :verb-go → PTRANS, …); adding *words* to an existing
   category is always safe, adding a new category requires a matching grammar arc."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def word-groups
  "category -> the surface words in it. Grouped by the CD primitive / function each
   category triggers. Add words by extending a vector."
  {;; ── verbs → CD primitive ACTs ──────────────────────────────────────────────
   :verb-give   ; ATRANS — transfer of possession
   ["gave" "give" "gives" "given" "giving"
    "handed" "donated" "donate" "donates" "lent" "lend" "lends"
    "sold" "sell" "sells" "bought" "buy" "buys" "offered" "offer" "offers"
    "delivered" "deliver" "delivers" "granted" "grant" "grants"
    "passed" "passes" "awarded" "award" "awards" "mailed" "mail" "mails"
    "sent" "send" "sends" "paid" "pay" "pays" "tipped" "tips" "bequeathed"]

   :verb-go     ; PTRANS — transfer of physical location (self-motion, needs a destination)
   ["went" "go" "goes" "going" "gone" "ran" "run" "runs" "running"
    "moved" "moves" "moving" "drove" "drive" "drives" "driving"
    "walked" "walk" "walks" "walking" "traveled" "travel" "travels"
    "flew" "fly" "flies" "swam" "swim" "swims" "marched" "march" "marches"
    "hurried" "hurry" "rushed" "rush" "rushes" "crawled" "crawl"
    "wandered" "wander" "journeyed" "journey" "sped" "strolled" "stroll"]

   :verb-depart ; intransitive PTRANS (no destination) — chiefly for embedded clauses
   ["left" "leave" "leaves" "leaving" "departed" "depart" "departs"
    "exited" "exit" "exits" "fled" "flee" "flees" "vanished" "vanish" "disappeared"]

   :verb-tell   ; MTRANS — transfer of mental information (ditransitive / clausal)
   ["told" "tell" "tells" "telling" "said" "say" "says" "saying"
    "asked" "ask" "asks" "informed" "inform" "informs" "explained" "explain"
    "mentioned" "mention" "reported" "report" "reports" "announced" "announce"
    "promised" "promise" "warned" "warn" "warns" "replied" "reply"
    "answered" "answer" "remarked" "stated" "wrote" "write" "writes"]

   :verb-mbuild ; MBUILD — build new information / decisions / plans
   ["decided" "decide" "decides" "deciding" "concluded" "conclude" "concludes"
    "reasoned" "reason" "realized" "realize" "realizes" "determined" "determine"
    "judged" "judge" "inferred" "infer" "deduced" "deduce" "resolved" "resolve"
    "supposed" "suppose" "assumed" "assume" "believed" "believe" "believes"
    "reckoned" "reckon" "figured" "wondered"]

   :verb-push   ; PROPEL — application of physical force
   ["pushed" "push" "pushes" "pushing" "pulled" "pull" "pulls"
    "kicked" "kick" "kicks" "threw" "throw" "throws" "thrown"
    "shoved" "shove" "shoves" "tossed" "toss" "tosses" "hurled" "hurl"
    "dragged" "drag" "drags" "struck" "strike" "strikes" "slammed" "slam"
    "punched" "punch" "punches" "pressed" "press" "presses" "rolled" "roll"]

   :verb-move   ; MOVE — move a body part
   ["waved" "wave" "waves" "waving" "nodded" "nod" "nods"
    "shrugged" "shrug" "shrugs" "blinked" "blink" "blinks" "winked" "wink"
    "clapped" "clap" "claps" "bowed" "stretched" "stretch" "shook" "shake"
    "flexed" "flex" "gestured" "gesture"]

   :verb-ingest ; INGEST — take a substance into the body
   ["ate" "eat" "eats" "eating" "eaten" "drank" "drink" "drinks" "drinking" "drunk"
    "swallowed" "swallow" "swallows" "consumed" "consume" "consumes"
    "devoured" "devour" "devours" "chewed" "chew" "chews" "sipped" "sip" "sips"
    "gulped" "gulp" "gulps" "munched" "munch" "inhaled" "inhale" "nibbled" "nibble"]

   :verb-expel  ; EXPEL — force a substance out of the body
   ["spit" "spat" "spits" "vomited" "vomit" "vomits" "exhaled" "exhale" "exhales"
    "coughed" "cough" "coughs" "sweated" "sweat" "sweats" "bled" "bleed"
    "drooled" "drool" "perspired"]

   :verb-grasp  ; GRASP — grip an object
   ["grabbed" "grab" "grabs" "grabbing" "held" "hold" "holds" "holding"
    "clutched" "clutch" "clutches" "gripped" "grip" "grips" "seized" "seize"
    "snatched" "snatch" "snatches" "caught" "catch" "catches" "grasped" "grasp"
    "picked" "pick" "picks"]

   :verb-speak  ; SPEAK — produce sound / vocalization
   ["yelled" "yell" "yells" "shouted" "shout" "shouts" "spoke" "speak" "speaks"
    "sang" "sing" "sings" "screamed" "scream" "screams" "whispered" "whisper"
    "muttered" "mutter" "hollered" "holler" "cheered" "cheer" "chanted" "chant"
    "wailed" "wail" "recited" "recite"]

   :verb-attend ; ATTEND — focus a sense organ (prepositional: "… to/at X")
   ["listened" "listen" "listens" "looked" "look" "looks" "watched" "watch" "watches"
    "gazed" "gaze" "gazes" "stared" "stare" "stares" "glanced" "glance"
    "peered" "peer" "peeked" "peek" "attended" "attend"]

   :verb-perceive ; ATTEND — perception taking a direct clausal object ("saw the dog eat …")
   ["saw" "see" "sees" "seeing" "seen" "noticed" "notice" "notices"
    "spotted" "spot" "spots" "witnessed" "witness" "heard" "hear" "hears"
    "observed" "observe" "observes"]

   :verb-intrans ; a bare intransitive verb — only completes inside an embedded clause
   ["snore" "snored" "snores" "sleep" "slept" "sleeps" "bark" "barked" "barks"
    "cry" "cried" "cries" "laugh" "laughed" "laughs" "sneeze" "sneezed" "sneezes"
    "purr" "purred" "purrs" "growl" "growled" "whine" "whined" "howl" "howled"
    "yawn" "yawned" "smile" "smiled" "frown" "frowned" "weep" "wept"
    "giggle" "giggled" "sob" "sobbed" "shiver" "shivered" "tremble" "trembled"]

   ;; ── function words ──────────────────────────────────────────────────────────
   :prep     ["to" "at" "toward" "towards" "into" "onto" "unto"]
   :particle ["up" "out" "down" "off" "away" "over" "back"]
   :det      ["the" "a" "an" "her" "his" "my" "its" "their" "your" "our"
              "this" "that" "these" "those" "some" "another" "each" "every"]})

;; ── Building / extending the lexicon ────────────────────────────────────────────

(defn from-groups
  "Flatten a category→words map into the flat word→category map the tokenizer uses
   (keys lowercased). A word listed in two categories takes the last one seen."
  [groups]
  (into {} (for [[cat words] groups
                 w words]
             [(str/lower-case w) cat])))

(defn add-words
  "Return `lex` (a flat word→category map) with each of `words` mapped to `category`."
  [lex category words]
  (into lex (for [w words] [(str/lower-case w) category])))

(defn merge-groups
  "Merge category→words maps, concatenating the word vectors of shared categories.
   Later maps win on order; use to layer extra words onto `word-groups`."
  [& groups-maps]
  (apply merge-with (comp vec concat) groups-maps))

(defn load-groups
  "Read a category→words EDN map from a file path or classpath resource, e.g.
   {:verb-give [\"gifted\"] :det [\"yon\"]}. Returns {} if it isn't found."
  [path]
  (if-let [src (or (some-> (io/file path) (#(when (.exists ^java.io.File %) %)))
                   (io/resource path))]
    (with-open [r (io/reader src)]
      (edn/read-string (slurp r)))
    {}))

(def categories
  "The category keys the grammar knows about (for validation when adding words)."
  (set (keys word-groups)))

;; The flat lexicon the tokenizer consumes. Optionally layer an external
;; `atndev/lexicon-extra.edn` (classpath) on top, so words can be added as data.
(def lexicon
  (from-groups (merge-groups word-groups (load-groups "atndev/lexicon-extra.edn"))))
