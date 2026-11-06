import type { Primitive } from "./types";

export interface Sample {
  sentence: string;
  /** What the grammar is expected to produce, shown as a hint before parsing. */
  expect?: Primitive;
  note?: string;
}

export interface SampleGroup {
  title: string;
  description: string;
  samples: Sample[];
}

export const SAMPLE_GROUPS: SampleGroup[] = [
  {
    title: "The 11 primitive ACTs",
    description: "One sentence per primitive. Each verb maps straight onto its ACT.",
    samples: [
      { sentence: "John gave Mary the book", expect: "ATRANS" },
      { sentence: "John went to the store", expect: "PTRANS" },
      { sentence: "John told Mary a story", expect: "MTRANS" },
      { sentence: "Mary decided", expect: "MBUILD" },
      { sentence: "John pushed the cart", expect: "PROPEL" },
      { sentence: "Mary waved her hand", expect: "MOVE" },
      { sentence: "Mary grabbed the hammer", expect: "GRASP" },
      { sentence: "John ate the apple", expect: "INGEST" },
      { sentence: "The baby spit up the milk", expect: "EXPEL" },
      { sentence: "John yelled fire", expect: "SPEAK" },
      { sentence: "Mary listened to the music", expect: "ATTEND" },
    ],
  },
  {
    title: "Embedded conceptualizations",
    description: "A role filled by a whole event. The ATN pushes into a sub-network and nests the frame.",
    samples: [
      { sentence: "Mary saw the dog eat the bone", expect: "ATTEND", note: "perceived event" },
      { sentence: "Mary listened to the dog snore", expect: "ATTEND", note: "intransitive clause" },
      { sentence: "John said Mary left", expect: "MTRANS", note: "reported event" },
      { sentence: "John decided to leave", expect: "MBUILD", note: "implied subject" },
    ],
  },
  {
    title: "Outside the grammar (so far)",
    description:
      "Paraphrases that CD theory says mean the same as “John gave Mary the book”. The parser doesn't handle these yet.",
    samples: [
      { sentence: "Mary received a book from John", note: "inverse verb" },
      { sentence: "John handed the book over", note: "particle verb" },
      { sentence: "The book was given to Mary", note: "passive" },
    ],
  },
];
