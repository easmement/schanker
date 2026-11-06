(ns atndev.lexicon-test
  "Locks in the extracted lexicon: the group→flat inversion, the extension helpers,
   and the invariant that no word lands in two categories (which would make
   tokenization nondeterministic). Run: clojure -M:dev:test"
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [atndev.lexicon :as lex]))

(deftest from-groups-inverts-and-lowercases
  (is (= {"gave" :verb-give "give" :verb-give "the" :det}
         (lex/from-groups {:verb-give ["Gave" "give"] :det ["THE"]}))))

(deftest add-words-extends
  (let [lx (lex/add-words {} :verb-give ["gifted" "Gifted"])]
    (is (= :verb-give (get lx "gifted")))
    (is (= :verb-give (get lx "gifted")))))       ; lowercased key

(deftest merge-groups-concatenates
  (is (= {:det ["the" "yon"]}
         (lex/merge-groups {:det ["the"]} {:det ["yon"]}))))

(deftest no-word-in-two-categories
  (testing "every word belongs to exactly one category (deterministic tokenization)"
    (let [pairs (mapcat (fn [[cat words]] (map (fn [w] [(str/lower-case w) cat]) words))
                        lex/word-groups)
          dups  (for [[w es] (group-by first pairs)
                      :when (> (count (distinct (map second es))) 1)]
                  [w (distinct (map second es))])]
      (is (empty? dups) (str "words in multiple categories: " (vec dups))))))

(deftest lexicon-is-built-and-covers-the-primitives
  (testing "the flat lexicon maps sample words to the categories the grammar needs"
    (is (= :verb-give   (get lex/lexicon "gave")))
    (is (= :verb-go     (get lex/lexicon "flew")))     ; a newly added word
    (is (= :verb-ingest (get lex/lexicon "devoured")))
    (is (= :det         (get lex/lexicon "another")))
    (is (nil? (get lex/lexicon "banana"))))            ; unknown -> nil (parser defaults :noun)
  (testing "the lexicon grew well past the original handful"
    (is (> (count lex/lexicon) 300))))
