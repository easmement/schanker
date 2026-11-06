/** The ATN's depth-first search, step by step: each state visited, the words left, the
 *  registers so far, the push/pop stack depth, and how many branches it fanned out into.
 *  This is backend cd/trace-parse's own output, shown as-is. */
export default function TraceView({ lines }: { lines: string[] }) {
  const steps = lines.filter((l) => /^\s*\d+\s/.test(l)).length;
  return (
    <details className="rounded-lg border border-black/10 dark:border-white/15">
      <summary className="cursor-pointer select-none px-4 py-2 text-sm font-medium">
        Parser trace ({steps} steps)
      </summary>
      <pre className="overflow-x-auto border-t border-black/10 px-4 py-3 font-mono text-xs leading-relaxed dark:border-white/15">
        {lines.join("\n")}
      </pre>
    </details>
  );
}
