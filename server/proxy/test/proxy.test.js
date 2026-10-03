import assert from "node:assert/strict";
import { test } from "node:test";
import { handle, LIMITED_TTL_SECONDS, LIVE_TTL_SECONDS, monthOf, TIMETABLE_TTL_SECONDS } from "../src/proxy.js";

function setup({ upstream = () => new Response('{"success":true}', { status: 200 }), limit = 10 } = {}) {
  const calls = [];
  const stored = new Map();
  const counts = new Map();
  const deps = {
    apiKey: "test-key",
    allowedCodes: new Set(["DR", "TNA"]),
    monthlyLimit: limit,
    now: () => new Date("2026-10-04T12:00:00Z"),
    fetch: async (url, init) => {
      calls.push({ url, init });
      return upstream();
    },
    cache: {
      match: async (key) => stored.get(key)?.response.clone(),
      put: async (key, response, ttl) => stored.set(key, { response, ttl }),
    },
    budget: {
      used: async (month) => counts.get(month) ?? 0,
      add: async (month) => counts.set(month, (counts.get(month) ?? 0) + 1),
    },
  };
  const get = (path, method = "GET") => handle(new Request(`https://proxy.example${path}`, { method }), deps);
  return { deps, calls, stored, counts, get };
}

test("a live board is fetched with the key and cached for a minute", async () => {
  const { calls, stored, get } = setup();

  const response = await get("/v1/stations/DR/live?hours=2");

  assert.equal(response.status, 200);
  assert.equal(await response.text(), '{"success":true}');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.railradar.in/v1/stations/DR/live?hours=2");
  assert.equal(calls[0].init.headers.Authorization, "Bearer test-key");
  assert.equal([...stored.values()][0].ttl, LIVE_TTL_SECONDS);
});

test("a repeat request is answered from the cache", async () => {
  const { calls, counts, get } = setup();

  await get("/v1/stations/DR/live?hours=2");
  const second = await get("/v1/stations/DR/live?hours=2");

  assert.equal(await second.text(), '{"success":true}');
  assert.equal(calls.length, 1);
  assert.equal(counts.get("2026-10"), 1);
});

test("a suburban train's position is fetched and cached for a minute", async () => {
  const { calls, stored, get } = setup();

  assert.equal((await get("/v1/trains/91006/live?haltsOnly=true")).status, 200);
  await get("/v1/trains/91006/live");

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.railradar.in/v1/trains/91006/live?haltsOnly=true");
  assert.equal([...stored.values()][0].ttl, LIVE_TTL_SECONDS);
});

test("a timetable is cached for a day", async () => {
  const { stored, get } = setup();

  await get("/v1/stations/TNA/trains");

  assert.equal([...stored.values()][0].ttl, TIMETABLE_TTL_SECONDS);
});

test("the key never appears in a response", async () => {
  const { get } = setup();

  const response = await get("/v1/stations/DR/live?hours=2");

  assert.equal([...response.headers.values()].some((value) => value.includes("test-key")), false);
});

test("anything the app does not ask for is refused without an upstream request", async () => {
  const { calls, get } = setup();

  for (const path of [
    "/",
    "/v1/lookup/stations",
    "/v1/trains/12951",
    "/v1/trains/12951/live", // not a suburban train
    "/v1/trains/91006/route",
    "/v1/trains/910066/live",
    "/v1/stations/NDLS/live?hours=2", // not a Mumbai suburban station
    "/v1/stations/dr/live?hours=2",
    "/v1/stations/DR/live?hours=24",
    "/v1/stations/DR/live?hours=abc",
    "/v1/stations/DR/live/extra",
  ]) {
    assert.equal((await get(path)).status, 404, path);
  }
  assert.equal((await get("/v1/stations/DR/trains", "POST")).status, 405);
  assert.equal(calls.length, 0);
});

test("extra query parameters do not create separate cache entries", async () => {
  const { calls, get } = setup();

  await get("/v1/stations/DR/live?hours=2");
  await get("/v1/stations/DR/live?hours=2&x=1");
  await get("/v1/stations/DR/live");

  assert.equal(calls.length, 1);
});

test("the monthly limit stops upstream requests", async () => {
  const { calls, get } = setup({ limit: 2 });

  await get("/v1/stations/DR/live?hours=2");
  await get("/v1/stations/TNA/live?hours=2");
  const third = await get("/v1/stations/TNA/trains");

  assert.equal(third.status, 429);
  assert.equal(calls.length, 2);
  // Boards already cached are still served.
  assert.equal((await get("/v1/stations/DR/live?hours=2")).status, 200);
});

test("an upstream limit is passed on and remembered for a while", async () => {
  const { calls, stored, get } = setup({ upstream: () => new Response('{"success":false}', { status: 429 }) });

  assert.equal((await get("/v1/stations/DR/live?hours=2")).status, 429);
  assert.equal((await get("/v1/stations/DR/live?hours=2")).status, 429);

  assert.equal(calls.length, 1);
  assert.equal([...stored.values()][0].ttl, LIMITED_TTL_SECONDS);
});

test("a rejected key is reported as a proxy fault, not as the phone's", async () => {
  const { stored, get } = setup({ upstream: () => new Response("", { status: 401 }) });

  assert.equal((await get("/v1/stations/DR/live?hours=2")).status, 502);
  assert.equal(stored.size, 0);
});

test("other upstream failures are passed on and not cached", async () => {
  const { stored, get } = setup({ upstream: () => new Response("down", { status: 503 }) });

  assert.equal((await get("/v1/stations/DR/live?hours=2")).status, 503);
  assert.equal(stored.size, 0);
});

test("an unreachable upstream gives 502", async () => {
  const { get } = setup({
    upstream: () => {
      throw new Error("network");
    },
  });

  assert.equal((await get("/v1/stations/DR/live?hours=2")).status, 502);
});

test("a missing key is an error rather than an unauthenticated request", async () => {
  const { deps, calls } = setup();
  deps.apiKey = undefined;

  const response = await handle(new Request("https://proxy.example/v1/stations/DR/trains"), deps);

  assert.equal(response.status, 500);
  assert.equal(calls.length, 0);
});

test("requests are counted by month in Indian time", () => {
  assert.equal(monthOf(new Date("2026-10-31T18:29:00Z")), "2026-10");
  assert.equal(monthOf(new Date("2026-10-31T18:30:00Z")), "2026-11");
});
