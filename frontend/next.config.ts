import type { NextConfig } from "next";

// Where the Clojure API (backend: clojure -M:dev:server) listens. Next.js forwards /api/* there,
// so the browser only ever talks to this app's own origin: no CORS, and the API stays on localhost.
const schankerApi = process.env.SCHANKER_API ?? "http://127.0.0.1:3100";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${schankerApi}/api/:path*` }];
  },
};

export default nextConfig;
