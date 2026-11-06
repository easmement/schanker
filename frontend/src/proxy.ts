import { NextResponse, type NextRequest } from "next/server";

// DNS-rebinding defence: a page on evil.example can re-point its own domain at 127.0.0.1 and call
// this server from the browser. Browsers never let scripts set Host, so it still names the
// attacker's domain: reject it before the /api rewrite forwards anything to the Clojure API.
const ALLOWED_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

export function proxy(request: NextRequest) {
  let hostname = "";
  try {
    hostname = new URL(`http://${request.headers.get("host") ?? ""}`).hostname;
  } catch {}
  if (!ALLOWED_HOSTS.has(hostname)) {
    return NextResponse.json({ error: "Requests must be addressed to localhost" }, { status: 403 });
  }
  return NextResponse.next();
}
