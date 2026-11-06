import type { Primitive } from "./types";

/** Schank's 11 primitive ACTs, as glossed in backend/CD-ATN-PARSER.md. */
export const PRIMITIVES: Record<Primitive, { gloss: string; verbs: string }> = {
  ATRANS: { gloss: "transfer of possession", verbs: "give, take, buy" },
  PTRANS: { gloss: "transfer of physical location", verbs: "go, run, move" },
  MTRANS: { gloss: "transfer of mental information", verbs: "tell, say, read" },
  MBUILD: { gloss: "build new information from old", verbs: "decide, conclude" },
  PROPEL: { gloss: "apply physical force", verbs: "push, pull, kick" },
  MOVE: { gloss: "move a body part", verbs: "wave, nod" },
  GRASP: { gloss: "grip an object", verbs: "grab, hold" },
  INGEST: { gloss: "take a substance into the body", verbs: "eat, drink" },
  EXPEL: { gloss: "force a substance out of the body", verbs: "spit, cry" },
  SPEAK: { gloss: "produce a sound", verbs: "yell, sing" },
  ATTEND: { gloss: "direct a sense organ at a stimulus", verbs: "listen, look, see" },
};

/** Role slots in display order, with what each means in a CD frame. */
export const ROLES = [
  { key: "actor", label: "Actor", hint: "who performs the act" },
  { key: "action", label: "Action", hint: "the verb of an intransitive embedded clause" },
  { key: "object", label: "Object", hint: "what is acted on or transferred" },
  { key: "from", label: "From", hint: "source of the transfer" },
  { key: "to", label: "To", hint: "recipient, destination or what is perceived" },
] as const;
