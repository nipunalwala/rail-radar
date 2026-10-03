# Train Near Me: Architecture Plan

Status: plan, no code written yet. Last updated 2026-10-03.

## 1. What the project is

Train Near Me is an Android app for Mumbai suburban railway commuters. It runs in
the background, notices when the user is approaching a railway station, and
posts a notification listing the next trains departing from that station.

**Problem.** Commuters miss trains by seconds because they don't know what is
about to depart as they reach the station.

**Outcome.** A notification such as:

```
Near Dadar Station
CSMT    2 min   PF 9   +8 late
Thane   5 min   PF 8   on time
Kalyan  9 min   PF 8   +12 late
```

delivered early enough to change what the user does (walk faster, pick a
platform), with no interaction needed.

### Scope of the first version

- Mumbai suburban network: Western, Central and Harbour lines.
- Automatic proximity alert with next departures, platform and delay.
- A settings screen and a station detail screen.

### Out of scope for the first version

Favourite stations, journey preferences, "departing in X minutes" alerts,
widget, Wear OS, other cities. The design leaves room for them (section 12).

## 2. Requirements that shape the design

| Requirement | Design consequence |
|---|---|
| Background operation, low battery | OS geofencing, not continuous GPS polling |
| Fast notifications | Timetable cached on device; live call is one request |
| Graceful handling of unavailable live data | Timetable is the base, live data is an overlay |
| Replaceable data provider | App depends on a `TrainDataProvider` interface only |
| No repeat alerts at the same station | Per-station alert state machine (section 6) |
| Privacy-conscious | Location never leaves the device; only a station code is sent |

## 3. Technology choices

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose | Native access to geofencing and background limits |
| Location | Google Play services `GeofencingClient` | Lowest-power proximity detection on Android |
| Background work | `BroadcastReceiver` + WorkManager (expedited) | Survives process death; allowed from background |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Standard, testable |
| Local storage | Room (timetable, stations), DataStore (settings, alert state) | |
| Dependency injection | Hilt | Lets the provider be swapped in one binding |
| Min SDK | 26 (Android 8.0) | Notification channels, modern background model |

## 4. High-level architecture

```
                 ┌────────────────────────────────────────────┐
                 │                 UI (Compose)               │
                 │  Home · Station detail · Settings · Onboard│
                 └───────────────────┬────────────────────────┘
                                     │ ViewModels
┌──────────────┐   enter/exit  ┌─────▼──────────────┐   ┌─────────────────┐
│ Geofence     ├──────────────►│ ProximityAlert     ├──►│ Notifier        │
│ Receiver     │               │ UseCase            │   │ (channels, text)│
└──────▲───────┘               └─────┬──────────────┘   └─────────────────┘
       │ registers                   │
┌──────┴───────┐               ┌─────▼──────────────┐
│ Geofence     │               │ DepartureRepository│  merges timetable + live
│ Manager      │               └──┬──────────────┬──┘
└──────▲───────┘                  │              │
       │                    ┌─────▼─────┐  ┌─────▼──────────────┐
┌──────┴───────┐            │ Room cache│  │ TrainDataProvider  │ (interface)
│ Station      │            │ timetable │  └─────┬──────────────┘
│ Repository   │            └───────────┘        │
│ (bundled DB) │                           ┌─────▼──────────────┐
└──────────────┘                           │ RailRadarProvider  │
                                           └────────────────────┘
```

### Modules (Gradle)

- `app`: UI, navigation, DI wiring, manifest, permissions flow.
- `core:model`: plain data classes (`Station`, `Departure`, `Line`).
- `core:data`: repositories, Room, DataStore, the `TrainDataProvider` interface.
- `provider:railradar`: the RailRadar implementation. The only module that
  knows RailRadar's URLs and JSON.
- `feature:proximity`: geofence manager, receiver, alert state machine, notifier.

A single-module project with these as packages is acceptable to start; the
package boundaries must still be respected so the split is cheap later.

## 5. Train data

### 5.1 Provider interface

```kotlin
interface TrainDataProvider {
    /** Scheduled departures for a station. Cacheable for days. */
    suspend fun timetable(stationCode: String): List<ScheduledDeparture>

    /** Live board for the next [hoursAhead] hours. Null fields mean "unknown". */
    suspend fun liveBoard(stationCode: String, hoursAhead: Int): List<LiveDeparture>
}
```

Domain model the rest of the app sees:

```kotlin
data class Departure(
    val trainNumber: String,
    val destinationName: String,
    val scheduledTime: LocalTime,
    val expectedTime: Instant?,     // null when no live data
    val delayMinutes: Int?,         // null when no live data
    val platform: String?,
    val trainType: TrainType,       // LOCAL, EXPRESS, ...
    val status: DepartureStatus,    // SCHEDULED, UPCOMING, AT_STATION, DEPARTED, UNKNOWN
    val isLive: Boolean,
)
```

Swapping providers means writing another implementation and changing one Hilt
binding. Nothing outside `provider:*` may import provider-specific types.

### 5.2 RailRadar: what was verified

Tested on 2026-10-03 at 23:01 IST against station `DR` (Dadar, Central).

| Endpoint | Result |
|---|---|
| `GET https://api.railradar.in/v1/stations/DR/live?hours=2` | HTTP 200, 47 trains, 30 of them `EMU` (locals) |
| `GET https://api.railradar.in/v1/stations/DR/trains` | HTTP 200, 598 trains, 451 `EMU`, about 208 KB |

Auth is `Authorization: Bearer <key>`.

Findings from the live board:

- Locals are included and carry real live data. 21 of the 30 locals had
  `delayMinutes`, with values from 0 to 43.
- 43 of 47 entries had a platform.
- Each entry has `train` (number, name, type, source, destination, runDays),
  `stop` (scheduled arrival/departure, platform) and `live` (`type`,
  `expectedDepartureTime`, `delayMinutes`, `departedAt`).
- `live.type` values seen: `scheduled`, `upcoming`, `not-started`,
  `at-station`, `departed`. `scheduled` means no live data for that train.
- `hours=2` returned a window from two hours back to two hours ahead, so
  departed trains are in the response and must be filtered out.
- Mainline expresses are mixed in. The app filters on `train.type`
  (`EMU`, `Suburban`) by default.
- The timetable board has no platform field; platform comes only from the
  live board.

One sample at one station late at night is not a coverage measurement. Peak
hours and the Western and Harbour lines still need checking (section 11).

### 5.3 Timetable base plus live overlay

1. `DepartureRepository.nextDepartures(station, count)` first reads the cached
   timetable from Room and computes the next departures for the current time
   and weekday. This works offline and takes milliseconds.
2. In parallel it calls `liveBoard`. If it returns within a short timeout
   (about 4 s), live entries replace their timetable counterparts, matched on
   train number.
3. If the live call fails or times out, the notification is posted from the
   timetable and labelled "scheduled times".
4. The timetable for a station is fetched the first time it is needed and
   refreshed weekly by a WorkManager job on unmetered network.

### 5.4 Request budget

The RailRadar free tier is documented as 1,000 requests per month per key.

- One live call per station entry, cached for 60 s.
- One timetable call per station per week, only for stations the user has
  actually been near.
- No periodic polling in the background.

A typical commuter (4 station entries a day) uses about 120 live calls a month.

**Release blocker:** a key compiled into the APK is shared by every install and
can be extracted. Before any public release, the app must call a small proxy
that holds the key, caches boards per station, and enforces limits. The proxy is
simply another `TrainDataProvider` implementation from the app's point of view.

During development the key lives in `local.properties` as `RAILRADAR_API_KEY`
and is exposed through `BuildConfig`.

## 6. Location and proximity

### 6.1 Station data

A bundled `stations.json` asset, loaded into Room on first run:

```
id, name, lat, lng, lines[], providerCodes[]
```

`providerCodes` is a list because interchange stations have one code per
railway. Dadar is `DR` on Central and `DDR` on Western, and is the only such
station on the three lines. For it the repository queries each code and merges
the boards.

The list holds 109 stations (Western 37, Central 51, Harbour 35, with 14 shared).
Coordinates are from OpenStreetMap (© OpenStreetMap contributors, ODbL), which
the app must credit. The code for locals is not always the mainline code:
Mumbai Central's locals are under `BCL`, not `MMCT`, and Kanjurmarg's under
`KJRD`, not `KJMG`.

### 6.2 Geofencing

- One circular geofence per station, radius from settings (default 500 m),
  transitions `ENTER` and `EXIT`.
- Android allows 100 geofences per app and the network has more stations than
  that. `GeofenceManager` registers the nearest ~90 stations on the lines the
  user monitors, plus one large "refresh" geofence around the user. Leaving the
  refresh geofence triggers re-registration around the new position.
- Geofences are re-registered on `BOOT_COMPLETED`, on app update, and when
  settings change (radius, lines, alerts on/off).
- No foreground service and no continuous GPS in the default mode.

### 6.3 Alert state machine

Per station, persisted in DataStore:

```
OUTSIDE ──ENTER──► alert sent ──► INSIDE ──EXIT──► OUTSIDE
```

- An alert is sent only on the `OUTSIDE → INSIDE` transition.
- A cooldown (default 10 min) after `EXIT` absorbs GPS jitter at the boundary.
- Entering a second station while inside another replaces the notification
  rather than stacking.

### 6.4 Riding through stations

A user already on a train enters every station's geofence along the route. To
avoid a notification at each stop, the alert is suppressed when the user has
just come from another station on the same line faster than about 20 km/h,
measured from when they left that station's radius to when they entered this
one's. The decision is made before any train data is fetched.

This replaces the original plan (speed above 20 km/h, or Activity Recognition
reporting `IN_VEHICLE`). Speed alone would also silence someone arriving by
auto-rickshaw or bus, who does want the alert, and Activity Recognition cannot
tell a train from a road vehicle while costing another runtime permission.

Known gaps, to be tuned in the field: changing trains at an interchange gets no
alert, because the user arrived there by train; and stations whose radii
overlap (about 1 km apart at 500 m) cannot be judged by timing, so a ride
between them relies on the reported speed.

### 6.5 Latency

Android may delay background geofence events by a couple of minutes to save
power. Mitigations: `setNotificationResponsiveness(0)`, the user-configurable
radius, and an optional "high accuracy mode" (foreground service with a
persistent notification) offered in settings as an explicit battery trade-off.

## 7. Alert flow, end to end

1. `GeofenceBroadcastReceiver` receives `ENTER` for a station.
2. It checks: alerts enabled, station's line monitored, state machine allows,
   not riding a train.
3. It enqueues an expedited `StationAlertWorker` with the station id.
4. The worker calls `DepartureRepository.nextDepartures`.
5. `Notifier` posts the notification. Tapping it opens the station detail
   screen with the full board.
6. On `EXIT` the notification is cancelled and the state returns to `OUTSIDE`.

## 8. User interface

- **Onboarding:** explains and requests, in order, notifications, foreground
  location, then background location ("Allow all the time"), each with a reason.
- **Home:** alert status, nearest station, its next departures, manual refresh.
- **Station detail:** full board with destination, time, platform, delay, type;
  marks each row as live or scheduled.
- **Settings:** proximity radius, number of trains shown, sound, vibration,
  automatic alerts on/off, lines to monitor, high accuracy mode.

## 9. Permissions

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`,
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`, and for high
accuracy mode `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`. Play Store requires
a justification and a prominent in-app disclosure for background location.

If background location is denied, the app still works when opened, and says so.

## 10. Privacy and failure handling

**Privacy**

- Coordinates stay on the device. The only thing sent to a server is a station
  code, which is also what a manual lookup would send.
- No location history is stored; only the current per-station alert state.
- No analytics or accounts in the first version.

**Failures**

| Situation | Behaviour |
|---|---|
| No network | Notification from cached timetable, labelled "scheduled times" |
| Live call slow or errors | Same, after the timeout |
| No cached timetable and no network | Short notification: near station, data unavailable |
| HTTP 429 (quota) | Back off until next day; fall back to timetable |
| HTTP 401 | Surface a one-time error in the app, not a notification per station |
| Location permission revoked | Alerts stop; home screen explains how to re-enable |

## 11. Testing and open questions

**Testing**

- Unit: next-departure calculation across midnight and by weekday, timetable
  and live merge, alert state machine, RailRadar JSON mapping from recorded
  fixtures.
- Instrumented: geofence receiver to notification with a fake provider and mock
  locations.
- Field: real commutes on each line, measuring time from crossing the radius to
  notification.

**Open questions**

1. Live coverage for locals at peak hours and on Western and Harbour lines.
2. Provider codes for every station, especially the two-code interchanges.
3. Whether fast/slow can be derived reliably (the API reports type `EMU`; some
   train names contain "Fast").
4. Real-world geofence latency and whether high accuracy mode should be default.
5. RailRadar terms for use in a distributed app, and paid tier pricing.

## 12. Milestones

1. **Skeleton:** project setup, models, `TrainDataProvider`, RailRadar provider
   with fixture tests.
2. **Data:** station asset, Room cache, `DepartureRepository` with merge and
   fallback; station detail screen showing a real board.
3. **Proximity:** permissions flow, geofence manager, receiver, worker,
   notification, state machine.
4. **Settings and polish:** all settings, boot re-registration, riding
   suppression, error states.
5. **Field test and tune:** latency, battery, radius defaults.
6. **Pre-release:** key proxy, background location disclosure, Play listing.

Later: favourite stations, platform and "departing in X minutes" alerts,
journey preferences, multiple nearby stations, delay notifications, widget,
Wear OS, other networks. Each fits the existing seams: more providers behind
the interface, more triggers into the alert use case, more surfaces reading
`DepartureRepository`.
