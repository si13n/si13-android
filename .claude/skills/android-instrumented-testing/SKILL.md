---
name: android-instrumented-testing
description: Android runtime testing practices for Forgetty when a real device/emulator and Android framework are required but UI interaction is not the primary signal. Use for Room migrations, Context, SharedPreferences, framework integration and instrumented helpers.
user-invocable: false
---

# Android instrumented testing

Location: `app/src/androidTest/`.

Use this level when correctness depends on Android runtime semantics but not primarily on
interacting with the real UI tree.

## Good candidates

- Room migration tests against real exported schemas/databases
- `Context` or Android resource behavior
- `SharedPreferences` and framework-backed persistence
- platform/service integration that needs instrumentation
- Android-only helpers that cannot be proven on the JVM

Do not use Espresso matchers merely because the test lives under `androidTest`. If UI is not
the behavior under test, keep the test focused on the runtime contract.

## State and determinism

- Never depend on execution order or leftovers from another test.
- Create/tear down databases and preferences explicitly.
- Control clocks/locales/data when they affect the assertion.
- Debug builds can seed demo tasks; read `app/src/androidTest/README.md` and existing isolation
  helpers before inventing another state-reset mechanism.
- Do not use `Thread.sleep()`. Wait on an actual asynchronous completion mechanism.

## Room migration contract

For schema changes, verify migration from the previous supported schema to the new schema with
real persisted data. A mocked migration does not prove user data survives an upgrade.

## Commands

```bash
./gradlew assembleDebugAndroidTest --console=plain
./gradlew connectedDebugAndroidTest --console=plain

# one class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.TaskDatabaseMigrationTest
```

Evidence is under `app/build/outputs/androidTest-results/connected/` and
`app/build/reports/androidTests/connected/`.
