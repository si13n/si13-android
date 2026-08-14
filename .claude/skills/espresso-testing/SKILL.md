---
name: espresso-testing
description: Espresso UI testing practices for Forgetty — deterministic Activity/Fragment/View interaction, stable resource ids, synchronization, assertions and UI test debugging. Use only when in-process Android UI behavior is the signal; pair with android-instrumented-testing for shared device/runtime setup.
user-invocable: false
---

# Espresso UI testing

Location: `app/src/androidTest/`.

Use Espresso when the requirement is specifically about in-process Android UI wiring or view
interaction: Activity/Fragment state, controls, visibility, navigation wiring, form behavior
or other UI outcomes where Espresso's synchronization and direct app-process access are useful.

For Room migrations, preferences or Context behavior without UI as the primary signal, use
`android-instrumented-testing` without adding Espresso ceremony.

## Current isolation contract

Debug builds can seed about 100 demo tasks after a data clear. Existing UI tests neutralize
that behavior before Activity launch and isolate state. Read `app/src/androidTest/README.md`
and `HomeTaskTest.clearState()` before changing setup.

## Rules

- Interact through stable resource ids/accessibility semantics.
- Assert the behavior/outcome, not only that a screen exists.
- Prefer Espresso synchronization/idling or an observable condition; never `Thread.sleep()`.
- Tests must not rely on seeded demo data, previous-test state or execution order.
- Keep pure logic in `app/src/test/` and non-UI Android-runtime contracts in focused
  instrumented tests.
- Do not mirror a Maestro journey unless Espresso buys a distinct, cheaper signal.

## Commands

```bash
./gradlew connectedDebugAndroidTest --console=plain

# one class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.HomeTaskTest

# one test
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.HomeTaskTest#addsGuestTask

scripts/run-espresso-with-allure.sh
```

Evidence:
- JUnit: `app/build/outputs/androidTest-results/connected/**/*.xml`
- HTML: `app/build/reports/androidTests/connected/`
- Allure: collected by `scripts/run-espresso-with-allure.sh`
