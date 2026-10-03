# Train Near Me

Android app that alerts Mumbai suburban commuters with the next trains when
they approach a station.

- Design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Build order and rules: [docs/BUILD_SPEC.md](docs/BUILD_SPEC.md)

Work is done one phase at a time from BUILD_SPEC.md. Read its "Rules for every
phase" before changing code, and finish and commit one phase before starting the
next.

Git:

- Push to `origin` (`main`) after every commit.
- Do not add a Claude `Co-Authored-By` line, or any other AI attribution, to
  commit messages.

Build and test:

```
./gradlew testDebugUnitTest assembleDebug
```

The RailRadar key is in `local.properties` (`RAILRADAR_API_KEY`). Never print,
log or commit it. The free tier is 1,000 requests a month, so tests use recorded
responses and live calls are made only when a phase allows them.
