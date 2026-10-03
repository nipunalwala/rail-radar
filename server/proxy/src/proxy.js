// The proxy's logic, kept free of Cloudflare APIs so it can be tested with node.
//
// It forwards the three RailRadar requests the app makes, adds the API key, and
// answers repeat requests from a cache so that many phones near the same
// station cost one upstream request.

export const UPSTREAM = "https://api.railradar.in";
export const LIVE_TTL_SECONDS = 60;
export const TIMETABLE_TTL_SECONDS = 24 * 60 * 60;
// How long an upstream "limit reached" answer is repeated without asking again.
export const LIMITED_TTL_SECONDS = 10 * 60;
export const MAX_HOURS = 4;

const PATH = /^\/v1\/stations\/([A-Z]{2,5})\/(live|trains)$/;
// Mumbai suburban train numbers have five digits and start with 9.
const TRAIN_PATH = /^\/v1\/trains\/(9\d{4})\/live$/;

function json(status, body, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", ...headers },
  });
}

const refuse = (status, message) => json(status, { success: false, error: message });

/**
 * Works out the upstream request for an incoming one, or returns null when the
 * request is not one the app makes.
 */
export function plan(url, allowedCodes) {
  const train = TRAIN_PATH.exec(url.pathname);
  if (train) return { path: `/v1/trains/${train[1]}/live?haltsOnly=true`, ttl: LIVE_TTL_SECONDS };

  const match = PATH.exec(url.pathname);
  if (!match) return null;
  const [, code, kind] = match;
  if (!allowedCodes.has(code)) return null;

  if (kind === "trains") {
    return { path: `/v1/stations/${code}/trains`, ttl: TIMETABLE_TTL_SECONDS };
  }
  const hours = Number(url.searchParams.get("hours") ?? "2");
  if (!Number.isInteger(hours) || hours < 1 || hours > MAX_HOURS) return null;
  return { path: `/v1/stations/${code}/live?hours=${hours}`, ttl: LIVE_TTL_SECONDS };
}

/** The month a request is counted in, as "2026-10", in Indian time. */
export function monthOf(now) {
  return new Date(now.getTime() + 5.5 * 60 * 60 * 1000).toISOString().slice(0, 7);
}

/**
 * deps:
 *   apiKey        RailRadar key
 *   allowedCodes  Set of station codes the app uses
 *   monthlyLimit  upstream requests allowed per month
 *   fetch         (url, init) => Response
 *   cache         { match(key): Response | undefined, put(key, response, ttlSeconds) }
 *   budget        { used(month): number, add(month): void }
 *   now           () => Date
 */
export async function handle(request, deps) {
  if (request.method !== "GET") return refuse(405, "Only GET is supported");
  if (!deps.apiKey) return refuse(500, "The proxy has no API key");

  const planned = plan(new URL(request.url), deps.allowedCodes);
  if (!planned) return refuse(404, "Not a request this proxy serves");

  const cacheKey = `https://proxy.cache${planned.path}`;
  const cached = await deps.cache.match(cacheKey);
  if (cached) return cached;

  const month = monthOf(deps.now());
  if ((await deps.budget.used(month)) >= deps.monthlyLimit) {
    return refuse(429, "Monthly request limit reached");
  }
  await deps.budget.add(month);

  let upstream;
  try {
    upstream = await deps.fetch(UPSTREAM + planned.path, {
      headers: { Authorization: `Bearer ${deps.apiKey}`, Accept: "application/json" },
    });
  } catch {
    return refuse(502, "The train data service could not be reached");
  }

  // A rejected key is the proxy's problem, not the phone's: the app must not
  // tell its user that their key is wrong.
  if (upstream.status === 401 || upstream.status === 403) {
    return refuse(502, "The train data service rejected the proxy");
  }

  const body = await upstream.text();
  const ttl = upstream.status === 200 ? planned.ttl : upstream.status === 429 ? LIMITED_TTL_SECONDS : 0;
  const respond = () =>
    new Response(body, {
      status: upstream.status,
      headers: { "content-type": "application/json", "cache-control": `public, max-age=${ttl}` },
    });
  if (ttl > 0) await deps.cache.put(cacheKey, respond(), ttl);
  return respond();
}
