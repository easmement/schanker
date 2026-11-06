(ns atndev.core-test
  "Locks in the four Conceptual Dependency primitives the CD ATN can parse:
   ATRANS, PTRANS, MTRANS, PROPEL. Each case asserts that a natural-language
   sentence produces the exact Schankian CD structure. Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [atndev.core :as cd]))

(deftest atrans
  (testing "ATRANS — transfer of possession (ditransitive: actor/verb/recipient/object)"
    (is (= {:primitive :ATRANS :actor "John" :object "book" :from "John" :to "Mary"}
           (cd/parse-cd "John gave Mary the book")))
    (testing "a determiner-led recipient (\"the cat\") parses too"
      (is (= {:primitive :ATRANS :actor "John" :object "bone" :from "John" :to "cat"}
             (cd/parse-cd "John gave the cat a bone"))))))

(deftest ptrans
  (testing "PTRANS — physical motion; self-motion so the object moving IS the actor"
    (is (= {:primitive :PTRANS :actor "John" :object "John" :to "store"}
           (cd/parse-cd "John went to the store")))
    (testing "destination without a determiner still parses"
      (is (= {:primitive :PTRANS :actor "Mary" :object "Mary" :to "school"}
             (cd/parse-cd "Mary ran to school"))))))

(deftest mtrans
  (testing "MTRANS — transfer of mental information; reuses the ditransitive path"
    (is (= {:primitive :MTRANS :actor "John" :object "story" :from "John" :to "Mary"}
           (cd/parse-cd "John told Mary a story")))
    (testing "determiner-led recipient works on the shared ditransitive path"
      (is (= {:primitive :MTRANS :actor "John" :object "story" :from "John" :to "child"}
             (cd/parse-cd "John told the child a story"))))))

(deftest propel
  (testing "PROPEL — application of physical force (transitive: actor/verb/object, no recipient)"
    (is (= {:primitive :PROPEL :actor "John" :object "cart"}
           (cd/parse-cd "John pushed the cart")))
    (testing "object without a determiner still parses"
      (is (= {:primitive :PROPEL :actor "Mary" :object "ball"}
             (cd/parse-cd "Mary kicked ball"))))))

(deftest move
  (testing "MOVE — move a body part (transitive frame; possessive determiner)"
    (is (= {:primitive :MOVE :actor "Mary" :object "hand"}
           (cd/parse-cd "Mary waved her hand")))))

(deftest ingest
  (testing "INGEST — take a substance into the body (transitive frame)"
    (is (= {:primitive :INGEST :actor "John" :object "apple"}
           (cd/parse-cd "John ate the apple")))))

(deftest expel
  (testing "EXPEL — force something out of the body (determiner-led subject + verb particle)"
    (is (= {:primitive :EXPEL :actor "baby" :object "milk"}
           (cd/parse-cd "The baby spit up the milk")))))

(deftest grasp
  (testing "GRASP — grip an object (transitive frame)"
    (is (= {:primitive :GRASP :actor "Mary" :object "hammer"}
           (cd/parse-cd "Mary grabbed the hammer")))))

(deftest speak
  (testing "SPEAK — produce vocalization (transitive frame, no determiner)"
    (is (= {:primitive :SPEAK :actor "John" :object "fire"}
           (cd/parse-cd "John yelled fire")))))

(deftest attend
  (testing "ATTEND — focus a sense organ (reuses the destination frame via to/at)"
    (is (= {:primitive :ATTEND :actor "Mary" :to "music"}
           (cd/parse-cd "Mary listened to the music")))
    (testing "the 'at' preposition also works"
      (is (= {:primitive :ATTEND :actor "Mary" :to "sky"}
             (cd/parse-cd "Mary looked at the sky"))))))

(deftest mbuild
  (testing "MBUILD — build new information/decision (intransitive here)"
    (is (= {:primitive :MBUILD :actor "Mary"}
           (cd/parse-cd "Mary decided")))))

(deftest attend-embedded-clause
  (testing "ATTEND's :to can be a nested conceptualization, not just a noun.
            \"Mary listened to [the dog snore]\" pushes into the :CLAUSE sub-net
            (det? subject verb) and pops the nested CD back as :to."
    (is (= {:primitive :ATTEND :actor "Mary" :to {:actor "dog" :action "snore"}}
           (cd/parse-cd "Mary listened to the dog snore")))
    (testing "the plain-noun destination still parses (no trailing verb)"
      (is (= {:primitive :ATTEND :actor "Mary" :to "music"}
             (cd/parse-cd "Mary listened to the music"))))))

(deftest recursive-clause-complement
  (testing "a complement can carry its OWN primitive by recursing into :S, so :to
            holds a full nested CD, not just {actor+action}"
    (testing "prepositionless perception verb (\"saw [the dog eat the bone]\")"
      (is (= {:primitive :ATTEND :actor "Mary"
              :to {:primitive :INGEST :actor "dog" :object "bone"}}
             (cd/parse-cd "Mary saw the dog eat the bone"))))
    (testing "the same recursion via the \"to\" complement path"
      (is (= {:primitive :ATTEND :actor "Mary"
              :to {:primitive :INGEST :actor "dog" :object "bone"}}
             (cd/parse-cd "Mary listened to the dog eat the bone"))))
    (testing "a perception verb with a plain-noun object still works"
      (is (= {:primitive :ATTEND :actor "Mary" :to "dog"}
             (cd/parse-cd "Mary saw the dog"))))
    (testing "the bare-intransitive :CLAUSE path is unaffected"
      (is (= {:primitive :ATTEND :actor "Mary" :to {:actor "dog" :action "snore"}}
             (cd/parse-cd "Mary listened to the dog snore"))))
    (testing "a determiner-led-recipient ditransitive nests correctly"
      (is (= {:primitive :ATTEND :actor "Mary"
              :to {:primitive :ATRANS :actor "dog" :from "dog" :to "cat" :object "bone"}}
             (cd/parse-cd "Mary saw the dog give the cat a bone"))))
    (testing "arbitrary depth: three stacked perception verbs over an inner INGEST"
      (is (= {:primitive :ATTEND :actor "Mary"
              :to {:primitive :ATTEND :actor "dog"
                   :to {:primitive :ATTEND :actor "cat"
                        :to {:primitive :INGEST :actor "bird" :object "worm"}}}}
             (cd/parse-cd "Mary saw the dog see the cat see the bird eat the worm"))))))

(deftest mtrans-clausal-complement
  (testing "MTRANS transfers a whole conceptualization: \"John said [Mary left]\"
            makes :object the nested event, not a flat noun (recurse into :S)"
    (is (= {:primitive :MTRANS :actor "John" :from "John"
            :object {:primitive :PTRANS :actor "Mary" :object "Mary"}}
           (cd/parse-cd "John said Mary left")))
    (testing "the embedded clause can be any full frame"
      (is (= {:primitive :MTRANS :actor "John" :from "John"
              :object {:primitive :INGEST :actor "Mary" :object "apple"}}
             (cd/parse-cd "John said Mary ate the apple"))))
    (testing "the plain ditransitive MTRANS is unaffected"
      (is (= {:primitive :MTRANS :actor "John" :from "John" :to "Mary" :object "story"}
             (cd/parse-cd "John told Mary a story"))))))

(deftest mbuild-thought-complement
  (testing "MBUILD builds a nested thought/plan. An infinitive (\"decided to leave\")
            has no surface subject, so the decider is seeded as the embedded actor."
    (is (= {:primitive :MBUILD :actor "John"
            :object {:primitive :PTRANS :actor "John" :object "John"}}
           (cd/parse-cd "John decided to leave")))
    (testing "the infinitive verb frame is general, not just departure"
      (is (= {:primitive :MBUILD :actor "John"
              :object {:primitive :INGEST :actor "John" :object "cake"}}
             (cd/parse-cd "John decided to eat the cake"))))
    (testing "intransitive MBUILD (\"Mary decided\") still parses"
      (is (= {:primitive :MBUILD :actor "Mary"}
             (cd/parse-cd "Mary decided"))))))

(deftest tokenizer
  (testing "verbs get their CD category; unknown words default to :noun"
    (is (= [{:word "John" :cat :noun}
            {:word "gave" :cat :verb-give}
            {:word "Mary" :cat :noun}
            {:word "the"  :cat :det}
            {:word "book" :cat :noun}]
           (cd/tokenize "John gave Mary the book")))))
