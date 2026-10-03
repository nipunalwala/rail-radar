# Release checklist

What is left to do before Train Near Me can be given to other people. Items
marked "you" need an account, a secret or a decision that cannot be made from
this repository.

## 1. Proxy (you)

Deploy `server/proxy` (see its README) and set `PROXY_BASE_URL` in
`local.properties`. Release builds refuse to build without it.

The free RailRadar tier (1,000 requests a month, shared by all users) is enough
for a handful of testers, not for a public release.

## 2. Signing key (you)

Create a keystore once and keep it and its passwords safe: losing it means you
can never update the app.

```
keytool -genkeypair -v -keystore release.jks -alias trainnearme -keyalg RSA -keysize 2048 -validity 10000
```

`*.jks` is git-ignored. Then add to `local.properties`:

```
RELEASE_STORE_FILE=release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=trainnearme
RELEASE_KEY_PASSWORD=...
```

Without these, `assembleRelease` produces an unsigned APK.

## 3. Build

```
./gradlew testDebugUnitTest assembleRelease     # APK, for sideloading
./gradlew bundleRelease                         # AAB, for Play
```

## 4. Check the release build

- [ ] Install it on a phone and go through onboarding, the home screen, a
      station page, settings and a station alert. R8 removes and renames code in
      release builds only, so the debug build passing proves nothing here.
      **This has never been done.**
- [x] Confirm the key is absent: unzip the APK and search every file for the
      key. Done on 4 October 2026 for an unsigned release build: not found in
      141 files, and found in the debug build as a control.

## 5. Play Console (you)

- Privacy policy: host `docs/PRIVACY_POLICY.md` at a public URL.
- Data safety form. The app processes location on the device only and never
  sends it, which Play does not count as collection. Check the form's own
  definitions against the policy before submitting.
- Background location declaration. Play reviews this by hand and asks for a
  short video showing the feature. Suggested text:

  > Train Near Me alerts Mumbai suburban railway commuters with the next
  > departing trains when they arrive near a station. The alert must appear
  > without the user opening the app, typically while the phone is in a pocket
  > on the way to the station, so the app registers geofences around stations
  > and needs location access in the background to receive them. Location is
  > used on the device only and is never transmitted or stored.

  The video should show: the in-app explanation screen before the permission
  request, granting "Allow all the time", and a notification arriving with the
  app closed.
- Foreground service declaration (type: location) for high accuracy mode: the
  user turns it on in settings, and a permanent notification is shown while it
  runs.
- Store listing: screenshots, a 512 px icon and a feature graphic. The launcher
  icon in the app is a plain placeholder (white train on blue).

## 6. Known gaps to decide on before release

See "Notes for later" in `BUILD_SPEC.md`: 106 station codes not checked against
a timetable, Trans-Harbour not included, line filtering at shared stations, and
boards that do not refresh by themselves.
