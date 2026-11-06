"use client";
import { useRef, useState } from "react";
import { ApiError, parseSentence } from "@/lib/api";
import type { ParseResult } from "@/lib/types";
import FrameView from "./FrameView";
import SamplePicker from "./SamplePicker";
import SentenceForm from "./SentenceForm";
import TokenStrip from "./TokenStrip";
import TraceView from "./TraceView";

type State =
  | { status: "idle" }
  | { status: "loading"; sentence: string }
  | { status: "done"; result: ParseResult }
  | { status: "error"; sentence: string; message: string };

/** The interactive part of the page: owns the request state and talks to the Clojure API.
 *  Everything it renders is presentational. */
export default function ParserWorkbench() {
  const [state, setState] = useState<State>({ status: "idle" });
  const [selected, setSelected] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0); // bumping it remounts the form with `selected`
  const [reading, setReading] = useState(0); // which parse to show when there are several
  const inFlight = useRef<AbortController | null>(null);

  async function run(raw: string) {
    const sentence = raw.trim();
    if (!sentence) return;
    // Latest request wins: clicking samples quickly must not let an older reply overwrite a newer one.
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;
    setReading(0);
    setState({ status: "loading", sentence });
    try {
      const result = await parseSentence(sentence, controller.signal);
      setState({ status: "done", result });
    } catch (e) {
      if (controller.signal.aborted) return;
      setState({ status: "error", sentence, message: e instanceof ApiError ? e.message : String(e) });
    }
  }

  function pickSample(sentence: string) {
    setSelected(sentence);
    setFormKey((k) => k + 1);
    run(sentence);
  }

  return (
    <div className="grid gap-8 md:grid-cols-[minmax(16rem,22rem)_1fr]">
      <aside className="md:sticky md:top-6 md:max-h-[calc(100vh-3rem)] md:self-start md:overflow-y-auto md:pr-2">
        <SamplePicker selected={selected} onSelect={pickSample} />
      </aside>

      <main className="min-w-0 space-y-6">
        <SentenceForm key={formKey} initial={selected ?? ""} busy={state.status === "loading"} onSubmit={run} />

        {/* Announce results to screen readers as they arrive. */}
        <section aria-live="polite" className="space-y-6">
          {state.status === "idle" && (
            <p className="opacity-70">
              Pick a sample sentence, or type your own, to see its Conceptual Dependency frame: the
              primitive ACT the verb triggers and the roles the parser fills.
            </p>
          )}

          {state.status === "loading" && <p className="opacity-70">Parsing “{state.sentence}”…</p>}

          {state.status === "error" && (
            <div role="alert" className="rounded-lg border border-red-500/50 bg-red-500/10 p-4">
              <p className="font-medium">Couldn’t parse “{state.sentence}”</p>
              <p className="text-sm">{state.message}</p>
            </div>
          )}

          {state.status === "done" && <Result result={state.result} reading={reading} onReading={setReading} />}
        </section>
      </main>
    </div>
  );
}

function Result({
  result,
  reading,
  onReading,
}: {
  result: ParseResult;
  reading: number;
  onReading: (i: number) => void;
}) {
  const frame = result.parses[reading] ?? result.cd;
  return (
    <>
      <div>
        <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide opacity-60">Tokens</h2>
        <TokenStrip tokens={result.tokens} />
      </div>

      <div>
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-sm font-semibold uppercase tracking-wide opacity-60">Conceptual Dependency</h2>
          {result.parses.length > 1 && (
            <div className="flex gap-1 text-sm" role="group" aria-label="Choose a reading">
              {result.parses.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => onReading(i)}
                  aria-pressed={i === reading}
                  className={`rounded px-2 py-0.5 ${i === reading ? "bg-indigo-600 text-white" : "border border-black/15 dark:border-white/20"}`}
                >
                  Reading {i + 1}
                </button>
              ))}
            </div>
          )}
        </div>
        {frame ? (
          <FrameView frame={frame} />
        ) : (
          <div className="rounded-lg border border-amber-500/50 bg-amber-500/10 p-4">
            <p className="font-medium">No parse</p>
            <p className="text-sm">
              This sentence is outside what the grammar covers so far. Conceptual Dependency says “Mary
              received a book from John” means the same as “John gave Mary the book”, but the ATN
              doesn’t yet have arcs for inverse verbs, passives or particle verbs. The trace below
              shows where every path stopped.
            </p>
          </div>
        )}
      </div>

      <TraceView lines={result.trace} />

      <details className="rounded-lg border border-black/10 dark:border-white/15">
        <summary className="cursor-pointer select-none px-4 py-2 text-sm font-medium">Raw JSON</summary>
        <pre className="overflow-x-auto border-t border-black/10 px-4 py-3 font-mono text-xs dark:border-white/15">
          {JSON.stringify(frame, null, 2)}
        </pre>
      </details>
    </>
  );
}
