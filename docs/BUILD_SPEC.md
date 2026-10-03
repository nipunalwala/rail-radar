# Train Near Me: Build Spec

This file defines the order in which the app is built and what "done" means at
each step. The design itself is in [ARCHITECTURE.md](ARCHITECTURE.md); this file
only says how to get there.

Each phase below is a self-contained prompt. To build the app, run the phases in
order, one at a time: "Do phase N of docs/BUILD_SPEC.md".

## Rules for every phase

1. **One phase at a time.** Do not start work that belongs to a later phase,
   even if it looks convenient.
2. **Read first.** Read this file, ARCHITECTURE.md and the code the phase
   touches before writing anything.
3. **Stay in scope.** Build only the phase's deliverables. Anything else worth
   doing goes in "Notes for later" at the bottom of this file.
4. **Verify before reporting.** A phase is done only when its acceptance checks
   pass, including:
   ```
   ./gradlew testDebugUnitTest assembleDebug
   ```
   If a check cannot be run (for example it needs a phone), say so explicitly.
5. **No live API calls in tests.** Tests use recorded responses in
   `app/src/test/resources/`. A live RailRadar call is made only when a phase
   lists it under "API budget", and the count is reported.
6. **The API key is never printed, logged or committed.** It lives in
   `local.properties` only.
7. **Respect the seams.** Only `provider/railradar` may know RailRadar's URLs
   or JSON. UI never calls a provider directly once the repository exists.
8. **Update the tracker.** When a phase passes, mark it in the progress table
   and record anything the next phase needs to know.
9. **Stop and report.** After each phase: what was built, what was verified and
   how, what was not verified, and any deviation from ARCHITECTURE.md. Commit
   the phase, then continue to the next one. Stop only when a phase needs a
   decision from the user or a device to test on. (The user chose this pace on
   2026-10-03.)
10. **Design changes go in the doc.** If a phase shows the architecture is
    wrong, update ARCHITECTURE.md in the same phase and call it out.

## Conventions

- Package root `com.trainnearme`; single Gradle module `app`, with packages
  `core.model`, `core.data`, `core.domain`, `provider.railradar`, `proximity`,
  `ui.<feature>`, `di`.
- Kotlin, Compose, Hilt, Retrofit, kotlinx.serialization, Room, DataStore,
  WorkManager. Versions live in `gradle/libs.versions.toml` only.
- Times: `java.time`, zone `Asia/Kolkata`. Never compare bare `LocalTime`
  across midnight; convert to `Instant` first.
- Pure logic (merging, filtering, state machines) is written as plain functions
  or classes with no Android imports, and unit tested.
- User-visible strings go in `strings.xml`.

## Progress

| Phase | Title | Status |
|---|---|---|
| 0 | Toolchain and first green build | Done 2026-10-03. `assembleDebug` passes; APK produced; git initialised |
| 1 | Provider skeleton | Done 2026-10-03. 6 unit tests pass. Board screen not yet seen on a device (none connected) |
| 2 | Station data | Done 2026-10-03. 109 stations; 13 unit tests pass in total. Database import not yet run on a device |
| 3 | Departure repository | Done 2026-10-03. 28 unit tests pass in total. Room queries, the refresh worker and the app start-up have not run on a device |
| 4 | Screens | Not started |
| 5 | Settings | Not started |
| 6 | Permissions and onboarding | Not started |
| 7 | Geofencing | Not started |
| 8 | Alert pipeline | Not started |
| 9 | Riding suppression and high accuracy mode | Not started |
| 10 | Failure handling and quota | Not started |
| 11 | Field test and tuning | Not started |
| 12 | Pre-release | Not started |

---

## Phase 0: Toolchain and first green build

**Goal.** The existing scaffold compiles and produces a debug APK.

**Starting state.** Android SDK (platform 35, build-tools 35.0.0) and Gradle
8.11.1 wrapper are installed. Gradle files, manifest, theme and source files
exist but have never been compiled.

**Do.**
- Run the build and fix compile and configuration errors only.
- Initialise git and make the first commit (confirm with the user first).

**Acceptance.**
- `./gradlew assembleDebug` succeeds.
- `app/build/outputs/apk/debug/app-debug.apk` exists.
- `local.properties` is not tracked by git.

**Out of scope.** Any new feature or refactor.

## Phase 1: Provider skeleton

**Goal.** RailRadar responses turn into domain objects, proven by tests.

**Do.**
- `Departure`, `ScheduledDeparture`, `TrainType`, `DepartureStatus`.
- `TrainDataProvider` interface with `timetable` and `liveBoard`.
- `RailRadarProvider`, DTOs, mapper, Retrofit API, Hilt binding.
- `upcomingDepartures`: drops departed trains, filters to locals, sorts by
  expected time, takes N.
- Temporary board screen fixed to Dadar Central (`DR`).

**Acceptance.**
- Unit tests pass against `live_DR.json` and `timetable_DR.json`: 47 trains of
  which 30 local; delay, platform and status mapped; blank platform is null;
  scheduled-only trains are not marked live; upcoming list is sorted and has no
  departed trains.
- With the APK on a phone, the screen lists Dadar's next locals. (Needs a
  device; report as unverified if none.)

**API budget.** 0 from the build. The app makes 1 call per screen load.

## Phase 2: Station data

**Goal.** The app knows every suburban station, its position, lines and
provider codes.

**Do.**
- Build `app/src/main/assets/stations.json`: id, name, lat, lng, lines
  (Western, Central, Harbour), provider codes (a list; interchanges have one
  per railway).
- Room entity, DAO and `StationRepository` with: all stations, by id, nearest
  to a coordinate, filtered by lines.
- Import the asset into Room on first run.

**Acceptance.**
- Every station on the three lines is present with coordinates; the count per
  line is reported and spot-checked against a published station list.
- Tests: nearest-station lookup, line filter, two-code interchange (Dadar).
- The source of the station data and its licence are recorded in this file.

**API budget.** Up to 10 calls to confirm codes for interchange stations.

**Decision (2026-10-03).** Coordinates come from OpenStreetMap.

**Result.**
- 109 stations: Western 37, Central 51, Harbour 35; 14 are on two lines.
  Line membership is listed by hand in `tools/stations/build-stations.mjs`.
- Coordinates: OpenStreetMap via the Overpass API, © OpenStreetMap
  contributors, ODbL. The app must show this credit (phase 4).
- Codes: confirmed present in RailRadar's `GET /v1/lookup/stations`.
- To regenerate: `node tools/stations/build-stations.mjs <osm.json>
  <railradar-stations.json>`, then bump the database version.
- API calls used: 5 (station directory, plus timetable boards for MMCT, BCL,
  KJMG and KJRD).

## Phase 3: Departure repository

**Goal.** One call returns the next trains for a station, live when possible
and from the timetable when not.

**Do.**
- Room cache of timetables per provider code, with fetch time.
- `DepartureRepository.nextDepartures(stationId, count)`:
  timetable base, live overlay matched on train number, 4 s live timeout,
  merged across a station's provider codes, 60 s in-memory live cache.
- Result says whether it is live, scheduled-only, or unavailable.
- Weekly timetable refresh worker (unmetered network).
- Replace the name-parsing destination fallback with station names from Room.

**Acceptance.**
- Tests with a fake provider: live success, live timeout, live error, no
  cache and no network, midnight rollover, weekday filtering by run days,
  two-code merge, cache hit within 60 s makes no second call.
- UI no longer calls `TrainDataProvider` directly.

**API budget.** 0.

## Phase 4: Screens

**Goal.** A usable app when opened by hand.

**Do.**
- Home: nearest station (one-shot location if permitted, else a station
  picker), its next departures, manual refresh, alert status.
- Station detail: full board; each row shows destination, minutes, platform,
  delay, and live or scheduled.
- Navigation between them; loading, empty and error states.

**Acceptance.**
- Screens render for: live data, scheduled-only, no data.
- ViewModel tests for state transitions.
- Screenshots of each state from a device or emulator (or reported unverified).

## Phase 5: Settings

**Goal.** Every setting in the spec exists, persists and is read by the app.

**Do.**
- DataStore-backed `SettingsRepository`: proximity radius (default 500 m),
  number of trains, sound, vibration, automatic alerts on/off, lines to monitor.
- Settings screen.
- Home and detail honour "number of trains" and "lines".

**Acceptance.**
- Tests: defaults, persistence round trip, change is observed.
- Changing lines changes which stations and trains appear.

## Phase 6: Permissions and onboarding

**Goal.** The app obtains notification, foreground and background location
permission in the required order, and behaves sensibly when refused.

**Do.**
- Onboarding flow with a reason before each request; background location
  requested only after foreground is granted.
- Prominent disclosure text for background location.
- Home shows what is missing and how to fix it.

**Acceptance.**
- Manual test matrix on a device: all granted; foreground only; denied;
  notifications denied. Results recorded here.

## Phase 7: Geofencing

**Goal.** The OS tells the app when the user enters or leaves a station's
radius, with no continuous GPS.

**Do.**
- `GeofenceManager`: nearest ~90 monitored stations plus one large refresh
  geofence; re-register on refresh exit, boot, app update and settings change.
- `GeofenceBroadcastReceiver` that logs enter and exit.
- Pure function that selects which stations to register, unit tested.

**Acceptance.**
- Tests for station selection (limit of 100, line filter, radius).
- With mock locations on a device or emulator, enter and exit events are
  logged for a station. Registration survives a reboot.

**Out of scope.** Notifications.

## Phase 8: Alert pipeline

**Goal.** Entering a station's radius produces one notification with the next
trains.

**Do.**
- Per-station alert state machine in DataStore (OUTSIDE, INSIDE, cooldown
  10 min after exit).
- Expedited `StationAlertWorker` calling `DepartureRepository`.
- `Notifier`: channel, sound and vibration from settings, text as in
  ARCHITECTURE.md section 1, tap opens station detail, cancelled on exit,
  replaced when a second station is entered.

**Acceptance.**
- Tests: state machine (enter, re-enter inside, exit, cooldown, re-enter after
  cooldown); notification text formatting for live, scheduled and unavailable.
- With mock locations: one notification on entry, none while staying, a new
  one after leaving and returning.

## Phase 9: Riding suppression and high accuracy mode

**Goal.** No alert at every stop while on a train; an opt-in faster mode.

**Do.**
- Suppress when trigger speed is above the threshold or Activity Recognition
  reports in-vehicle. The check runs before any API call.
- Optional foreground-service mode behind a setting, with an honest battery
  note.

**Acceptance.**
- Tests for the suppression decision.
- A real train ride produces no alerts at intermediate stations (field check).

## Phase 10: Failure handling and quota

**Goal.** Every row of the failure table in ARCHITECTURE.md section 10 behaves
as written.

**Do.**
- 429 backs off until the next day; 401 shows one in-app error.
- Request counter visible in a debug screen.
- Audit that no code path polls the API in the background.

**Acceptance.**
- Tests for each failure row using a fake provider or MockWebServer.

## Phase 11: Field test and tuning

**Goal.** Numbers, not impressions.

**Do.**
- On real commutes on each line: time from crossing the radius to
  notification, false alerts, missed alerts, battery use over a day.
- Compare RailRadar against the platform and Yatri for accuracy.
- Tune radius default, cooldown and speed threshold.

**Acceptance.** Results table recorded in this file; defaults updated.

## Phase 12: Pre-release

**Goal.** Safe to give to other people.

**Do.**
- Key proxy server and a `TrainDataProvider` that talks to it.
- App icon, release signing, R8 rules, privacy policy, Play background
  location declaration.

**Acceptance.** Release build contains no API key; decompiling confirms it.

---

## Notes for later

- **Local codes can differ from mainline codes.** RailRadar files Mumbai
  Central's locals under `BCL` (1,020 trains) while `MMCT` has none, and
  Kanjurmarg's under `KJRD` while `KJMG` is empty. A code existing in the
  directory does not prove it carries locals. Only Dadar (DR), Mumbai Central
  and Kanjurmarg have been checked against a timetable; the other 106 codes are
  unverified. Phase 3 should flag any station whose timetable has no locals.
- Trans-Harbour (Thane to Vashi/Panvel) and Nerul to Uran stations are not in
  the station list; the spec names only Western, Central and Harbour.
- There are 109 stations and Android allows 100 geofences, so phase 7's
  nearest-90 selection is required even with every line monitored.
- Parel/Prabhadevi (290 m), Lower Parel/Currey Road (346 m) and
  Matunga/Matunga Road (367 m) sit inside each other's 500 m radius. Phase 8
  must decide what one alert shows when two stations are entered together.
- Show the OpenStreetMap credit in the app (phase 4).
