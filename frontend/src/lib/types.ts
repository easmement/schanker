// Shapes of the Clojure API's JSON (backend/development/src/atndev/server.clj). Clojure keywords
// arrive as plain strings: :ATRANS -> "ATRANS", :verb-give -> "verb-give".

export type Primitive =
  | "ATRANS" | "PTRANS" | "MTRANS" | "MBUILD" | "PROPEL" | "MOVE"
  | "GRASP" | "INGEST" | "EXPEL" | "SPEAK" | "ATTEND";

/** A Conceptual Dependency frame. Role slots hold a word, or a whole nested frame when the
 *  sentence embeds an event ("Mary saw [the dog eat the bone]"). Frames from the :CLAUSE
 *  sub-network have no primitive, just an actor and an intransitive :action. */
export interface Frame {
  primitive?: Primitive;
  actor?: string;
  action?: string;
  object?: string | Frame;
  from?: string;
  to?: string | Frame;
}

export interface Token {
  word: string;
  cat: string; // lexicon category, e.g. "verb-give", "det", "noun"
}

export interface ParseResult {
  sentence: string;
  tokens: Token[];
  cd: Frame | null; // the first parse; null when the grammar can't parse the sentence
  parses: Frame[]; // every reading the backtracking search found
  trace: string[]; // the ATN agenda trace, one line per step
}
