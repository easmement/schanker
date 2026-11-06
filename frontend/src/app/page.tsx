import ParserWorkbench from "@/components/ParserWorkbench";

// A Server Component: the header is static HTML; only ParserWorkbench runs in the browser.
export default function Home() {
  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6">
      <header className="mb-8">
        <h1 className="text-2xl font-semibold">Schanker</h1>
        <p className="mt-1 max-w-3xl opacity-70">
          Parse English sentences into Roger Schank’s Conceptual Dependency frames with an
          Augmented Transition Network. The verb selects one of 11 primitive ACTs; the parser fills
          its roles, and nests whole events when a sentence embeds one.
        </p>
      </header>
      <ParserWorkbench />
    </div>
  );
}
