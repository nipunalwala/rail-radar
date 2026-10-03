# Field test (phase 11)

Nothing in the app's background behaviour has been seen working on a real
journey yet. This is the sheet for finding out. Use a **debug** build: it keeps
an event log and has a "Test alert" button and a diagnostics screen.

## Before leaving

- [ ] Onboarding finished; location is "Allow all the time"; notifications on.
- [ ] Battery: set the app to "Unrestricted" (or "Don't optimise") if the phone
      offers it. Note the phone model and whether you did this.
- [ ] Settings > Diagnostics: note the request count.
- [ ] Open a station page and tap "Test alert". A notification should arrive in
      a few seconds. If it does not, stop here: the fault is in the notification
      path and no journey will tell you more.
- [ ] Note the battery percentage and the time.

## On the journey

For each station you walk up to, write down the time you think you crossed the
radius (500 m is roughly 6 minutes' walk) and the time the notification arrived.

Things to try at least once:

| # | Situation | Expected |
|---|---|---|
| 1 | Walk to your home station | One alert, with trains that match the platform indicators |
| 2 | Stay near the station for 20 minutes | No second alert |
| 3 | Ride through 5 or more stations | No alerts for stations you pass through |
| 4 | Get off and walk out | No alert for the station you arrived at |
| 5 | Leave a station and come back after 15 minutes | A new alert |
| 6 | Arrive by auto or bus | An alert |
| 7 | Aeroplane mode, then walk to a station | An alert marked as timetable only, once the timetable has been saved by an earlier visit |
| 8 | Restart the phone, do not open the app, walk to a station | An alert |
| 9 | Parel/Prabhadevi, Lower Parel/Currey Road or Matunga/Matunga Road | One alert, for the closer station |
| 10 | Dadar, on each line | Trains for both lines, or only the monitored one |

## Afterwards

Pull the event log:

```
adb shell run-as com.trainnearme cat files/events.log
```

It shows each geofence entry and exit, each alert shown or held back and why.
Compare it with your notes, then fill in the table in `BUILD_SPEC.md` phase 11.

## Accuracy check

At a station, for the next 5 trains, compare the alert with the platform
indicator (and m-Indicator or Yatri if you have them):

| Station | Time | Train | App says | Indicator says | Left at |
|---|---|---|---|---|---|
| | | | | | |

A train is "right" if the destination matches and the departure time is within
2 minutes. Note wrong or missing platforms separately.

## What the numbers decide

| Measured | If | Then change |
|---|---|---|
| Delay from crossing the radius to the alert | Often over 3 minutes | Raise the default radius, or recommend high accuracy mode |
| Alerts while riding through | Any | Lower `RIDING_SPEED_MPS` (now 5.5) or lengthen `RIDE_WINDOW` (now 12 min) in `core/domain/Riding.kt` |
| Missed alerts on arrival by auto or bus | Any | Raise `RIDING_SPEED_MPS` |
| Repeat alerts at one station | Any | Lengthen `ALERT_COOLDOWN` (now 10 min) in `core/domain/AlertState.kt` |
| Battery over a day | Over 3% for this app | Check that high accuracy mode is off; report the event log |
| Requests in a month | Heading past 1,000 | Lengthen the live cache or shorten the alert list |
