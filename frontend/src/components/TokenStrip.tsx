import type { Token } from "@/lib/types";

/** The tokenizer's view of the sentence: each word with its lexicon category. Verbs are
 *  highlighted because their category is what selects the primitive ACT. */
export default function TokenStrip({ tokens }: { tokens: Token[] }) {
  return (
    <ol className="flex flex-wrap gap-2" aria-label="Tokens">
      {tokens.map((t, i) => {
        const verb = t.cat.startsWith("verb-");
        return (
          <li
            key={i}
            className={`rounded-md border px-2 py-1 text-center ${
              verb ? "border-indigo-500/60 bg-indigo-500/10" : "border-black/10 dark:border-white/15"
            }`}
          >
            <div className="font-mono text-sm">{t.word}</div>
            <div className="text-xs opacity-60">{t.cat}</div>
          </li>
        );
      })}
    </ol>
  );
}
