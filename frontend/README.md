# Schanker UI

A Next.js web UI for the Schanker Conceptual Dependency parser. Pick a sample sentence, or type
one, to see its tokens, its CD frame (nested when the sentence embeds an event), any alternative
parses, and the ATN parser's step-by-step trace.

```bash
# 1. the Clojure API, from ../backend (listens on 127.0.0.1:3100)
clojure -M:dev:server

# 2. this app (http://localhost:3000)
npm install
npm run dev
```

Checks: `npm run typecheck`, `npm run lint`, `npx next build`.

See **[UI-DESIGN.md](UI-DESIGN.md)** for the architecture: Next.js and React, how the browser
reaches the Clojure backend, the API contract, the component design and state management.
