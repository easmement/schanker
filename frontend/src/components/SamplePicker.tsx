import { SAMPLE_GROUPS } from "@/lib/samples";

/** Sample sentences, grouped by what they demonstrate. Clicking one parses it. */
export default function SamplePicker({
  selected,
  onSelect,
}: {
  selected: string | null;
  onSelect: (sentence: string) => void;
}) {
  return (
    <nav aria-label="Sample sentences" className="space-y-6">
      {SAMPLE_GROUPS.map((group) => (
        <section key={group.title}>
          <h2 className="text-sm font-semibold">{group.title}</h2>
          <p className="mb-2 text-xs opacity-60">{group.description}</p>
          <ul className="space-y-1">
            {group.samples.map((s) => {
              const active = s.sentence === selected;
              return (
                <li key={s.sentence}>
                  <button
                    type="button"
                    onClick={() => onSelect(s.sentence)}
                    aria-pressed={active}
                    className={`flex w-full items-baseline justify-between gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors ${
                      active
                        ? "bg-indigo-500/15 font-medium"
                        : "hover:bg-black/5 dark:hover:bg-white/10"
                    }`}
                  >
                    <span>{s.sentence}</span>
                    <span className="shrink-0 font-mono text-xs opacity-50">{s.expect ?? s.note}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </nav>
  );
}
