import { PRIMITIVES, ROLES } from "@/lib/primitives";
import type { Frame } from "@/lib/types";

const isFrame = (v: unknown): v is Frame => typeof v === "object" && v !== null;

// Nested frames alternate accent colours so each level of embedding is easy to follow.
const ACCENTS = [
  "border-indigo-500/60 bg-indigo-500/5",
  "border-emerald-500/60 bg-emerald-500/5",
  "border-amber-500/60 bg-amber-500/5",
];

/** A Conceptual Dependency frame as a card: the primitive ACT with its gloss, then one row per
 *  filled role. A role holding a whole event renders as a nested card (recursion), so
 *  "Mary saw the dog eat the bone" shows ATTEND containing INGEST. */
export default function FrameView({ frame, depth = 0 }: { frame: Frame; depth?: number }) {
  const info = frame.primitive ? PRIMITIVES[frame.primitive] : null;
  const roles = ROLES.filter((r) => frame[r.key] !== undefined);

  return (
    <div className={`rounded-lg border-l-4 border ${ACCENTS[depth % ACCENTS.length]} border-y-black/10 border-r-black/10 dark:border-y-white/10 dark:border-r-white/10 p-4`}>
      <div className="mb-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="rounded bg-foreground px-2 py-0.5 font-mono text-sm font-semibold text-background">
          {frame.primitive ?? "EVENT"}
        </span>
        <span className="text-sm opacity-70">
          {info ? info.gloss : "an intransitive event (embedded clause)"}
        </span>
      </div>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2">
        {roles.map(({ key, label, hint }) => {
          const value = frame[key];
          return (
            <div key={key} className="contents">
              <dt className="pt-0.5 text-sm font-medium opacity-70" title={hint}>
                {label}
              </dt>
              <dd className="min-w-0">
                {isFrame(value) ? (
                  <FrameView frame={value} depth={depth + 1} />
                ) : (
                  <span className="font-mono">{value}</span>
                )}
              </dd>
            </div>
          );
        })}
      </dl>
    </div>
  );
}
