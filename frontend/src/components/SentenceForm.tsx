import { useState } from "react";

/** Free-text entry. Remounted (via key) whenever a sample is chosen, so it starts from that
 *  sentence without syncing props into state. Not a "use client" entry: it's only rendered
 *  inside ParserWorkbench, which is, so it runs on the client and can take function props. */
export default function SentenceForm({
  initial,
  busy,
  onSubmit,
}: {
  initial: string;
  busy: boolean;
  onSubmit: (sentence: string) => void;
}) {
  const [text, setText] = useState(initial);
  return (
    <form
      className="flex gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(text);
      }}
    >
      <label htmlFor="sentence" className="sr-only">
        Sentence to parse
      </label>
      <input
        id="sentence"
        value={text}
        onChange={(e) => setText(e.target.value)}
        maxLength={300}
        placeholder="Type a sentence, e.g. John gave Mary the book"
        className="min-w-0 flex-1 rounded-md border border-black/15 bg-transparent px-3 py-2 outline-none focus:border-indigo-500 dark:border-white/20"
      />
      <button
        type="submit"
        disabled={busy || !text.trim()}
        className="rounded-md bg-indigo-600 px-4 py-2 font-medium text-white disabled:opacity-50"
      >
        {busy ? "Parsing…" : "Parse"}
      </button>
    </form>
  );
}
