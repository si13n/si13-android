---
name: espresso-testing
description: Project-specific instrumented/Espresso and Room testing practices for Forgetty — device test selection, deterministic state isolation, resource-id interaction, Room migrations, Allure outputs and debugging. Use for app/src/androidTest work.
---

# Espresso / instrumented testing in this repo

Location: `app/src/androidTest/`. Read `app/src/androidTest/README.md` before changing the
suite; it is the source of current test counts, isolation details and report paths.

Use this level when the behavior genuinely needs Android runtime state: Views, gestures,
`Context`, `SharedPreferences`, lifecycle or a real Room database/migration.

## Current isolation contract

Debug builds can seed about 100 demo tasks after a data clear. Existing instrumentation tests
neutralize that behavior before activity launch and clear state both before and after tests.
Do not invent a second isolation mechanism without reading `HomeTaskTest.clearState()`.

Rules:

- Never rely on seeded demo data, previous-test state or test execution order.
- Clean state before and after when the suite's pattern requires it.
- Prefer stable resource ids and Espresso synchronization; never use `Thread.sleep()` to
  hide a race.
- Assert the behavior under test, not only that a view exists.
- Room migration tests should use a real test database/schema path, not a mocked migration.
- Keep pure logic in `app/src/test/`; device tests are intentionally more expensive.

## Commands

```bash
./gradlew assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest --console=plain

# one class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.HomeTaskTest

# one test
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.HomeTaskTest#addsGuestTask

# same wrapper used for Allure collection
scripts/run-espresso-with-allure.sh
```

## Evidence

- JUnit: `app/build/outputs/androidTest-results/connected/**/*.xml`
- HTML: `app/build/reports/androidTests/connected/`
- Allure: collected by `scripts/run-espresso-with-allure.sh`

If there is no connected device, report the device-level result as NOT RUN / UNVERIFIED.
