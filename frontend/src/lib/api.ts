import type { ParseResult } from "./types";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/** POST /api/parse. The request goes to this app's own origin; next.config.ts rewrites it to the
 *  Clojure API. Pass an AbortSignal so a newer request can cancel an older one. */
export async function parseSentence(sentence: string, signal?: AbortSignal): Promise<ParseResult> {
  let res: Response;
  try {
    res = await fetch("/api/parse", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sentence }),
      signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "Can't reach the app server.");
  }
  if (!res.ok) {
    // The Clojure API answers {"error": "..."}; a proxy failure (API not running) may not be JSON.
    const body = await res.json().catch(() => null);
    const message =
      body?.error ??
      (res.status >= 500
        ? "The Schanker API isn't responding. Start it with: cd backend && clojure -M:dev:server"
        : res.statusText);
    throw new ApiError(res.status, message);
  }
  return res.json();
}
