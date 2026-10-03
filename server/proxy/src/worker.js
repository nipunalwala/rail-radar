// Cloudflare Worker entry point. All decisions are made in proxy.js.

import codes from "./codes.json";
import { handle } from "./proxy.js";

const allowedCodes = new Set(codes);
const DEFAULT_MONTHLY_LIMIT = 950;

export default {
  async fetch(request, env, ctx) {
    return handle(request, {
      apiKey: env.RAILRADAR_API_KEY,
      allowedCodes,
      monthlyLimit: Number(env.MONTHLY_LIMIT ?? DEFAULT_MONTHLY_LIMIT),
      fetch,
      now: () => new Date(),
      cache: {
        match: (key) => caches.default.match(key),
        // The response already carries the max-age the cache should honour.
        put: (key, response) => ctx.waitUntil(caches.default.put(key, response)),
      },
      // KV is eventually consistent, so the count can run a few requests
      // behind under load. MONTHLY_LIMIT leaves room for that.
      budget: {
        used: async (month) => Number((await env.BUDGET.get(month)) ?? 0),
        add: async (month) => {
          const used = Number((await env.BUDGET.get(month)) ?? 0);
          await env.BUDGET.put(month, String(used + 1), { expirationTtl: 70 * 24 * 60 * 60 });
        },
      },
    });
  },
};
