# Train data proxy

A Cloudflare Worker that sits between the app and RailRadar so that release
builds of the app carry no API key.

It serves exactly the two requests the app makes, with RailRadar's own paths and
response bodies:

- `GET /v1/stations/{code}/live?hours=N` (N from 1 to 4), cached for 60 seconds
- `GET /v1/stations/{code}/trains`, cached for 24 hours

Everything else is refused. Station codes are limited to the ones in the app's
station list (`src/codes.json`), so the key cannot be used for other stations.

Upstream requests are counted per month in a KV namespace. Once `MONTHLY_LIMIT`
is reached the proxy answers 429 without calling RailRadar, and the app falls
back to its saved timetable until the next day.

## Status

The logic in `src/proxy.js` is covered by `npm test` (node, no dependencies).
**The Worker has never been deployed.** `src/worker.js`, the Cache API calls and
the KV counter are untested until someone deploys it.

## Deploying

Needs a Cloudflare account and `npm install -g wrangler`.

```
cd server/proxy
wrangler login
wrangler kv namespace create BUDGET      # put the printed id in wrangler.toml
wrangler secret put RAILRADAR_API_KEY    # paste the key when asked
wrangler deploy
```

Then add the Worker's address to `local.properties`, ending in a slash:

```
PROXY_BASE_URL=https://train-near-me-proxy.<your-subdomain>.workers.dev/
```

Check it before building a release:

```
curl "https://train-near-me-proxy.<your-subdomain>.workers.dev/v1/stations/DR/live?hours=2"
```

## After changing the station list

```
npm run codes
wrangler deploy
```

## Limits to know about

- The free RailRadar tier is 1,000 requests a month for **all** users together.
  Caching helps when users share stations, but a public release needs a paid
  plan. Raise `MONTHLY_LIMIT` in `wrangler.toml` to match it.
- Anyone who finds the proxy address can call it. They can only get the same
  station boards the app shows, and cannot exceed the monthly limit, but they
  can use it up. Cloudflare rate limiting rules per IP address are the next step
  if that happens.
- The KV counter can lag by a few requests under load, which is why the default
  limit is 950 and not 1,000.
