# Instrumented tests (Espresso + Room)

On-device tests for behaviour that needs a real Android runtime: view inflation, gestures,
`SharedPreferences`, and Room migrations.

## Running

```bash
./gradlew connectedDebugAndroidTest              # whole suite (needs a device)
./gradlew assembleDebugAndroidTest               # just build the test APK

# one class / one test
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.LoginBottomSheetTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.si13.app.HomeTaskTest#addsGuestTask

# with Allure result collection (what CI runs)
scripts/run-espresso-with-allure.sh
```

Reports:

| Output | Path |
|---|---|
| HTML report | `app/build/reports/androidTests/connected/` |
| JUnit XML | `app/build/outputs/androidTest-results/connected/**/*.xml` |
| Allure results | `app/build/outputs/connected_android_test_additional_output/**/allure-results/` |

## The suite

| Class | Tests | Covers |
|---|---|---|
| `HomeTaskTest` | 36 | task creation, the 100-char limit, long-list scrolling, header layout, swipe/delete/undo, sorting, filters, list management |
| `LoginBottomSheetTest` | 4 | the sheet opens on unauthenticated launch, closes, "continue as guest", and does not reopen after background/foreground |
| `AppearancePreferencesTest` | 2 | appearance choice persists; modes map to `AppCompat` night modes |
| `MainActivityTest` | 1 | the app relaunches after a restart |
| `TaskDatabaseMigrationTest` | 1 | Room v4 → v5 preserves legacy rows and adds defaults |

**44 tests total.**

`TaskDatabaseMigrationTest` is the highest-value test in the repository. A bad migration is
irreversible and hits every existing user, so it is verified against a real SQLite database
rather than a mock.

## Test isolation — read this before adding a test

Debug builds seed **~100 demo tasks** on first launch after data is cleared
(`MainActivity.seedDebugTasksIfNeeded`, gated on `FLAG_DEBUGGABLE`). The suite neutralises
that in `HomeTaskTest.clearState()`, which runs `@Before` **and** `@After`:

```kotlin
FirebaseAuth.getInstance().signOut()
AuthRepository(context).clear()
ForgettyPreferences.create(context).clear()
context.getSharedPreferences("forgetty_task_lists", MODE_PRIVATE).edit().clear().commit()
// Disable the one-time demo seed so instrumentation starts from an empty database.
context.getSharedPreferences("MainActivity", MODE_PRIVATE)
    .edit().putBoolean(MainActivity.DEMO_SEED_KEY, true).commit()
runBlocking { TaskDatabase.getInstance(context).taskDao().deleteAll() }
```

Setting `DEMO_SEED_KEY` to `true` **before** the Activity launches makes the seeder think it
has already run. Combined with `deleteAll()`, every test starts from an empty database.

Rules for new tests:

- Clear state in both `@Before` and `@After`. Cleaning up only afterwards leaves the first
  test at the mercy of whatever the previous run left behind.
- Never assert on seeded demo data.
- Never rely on execution order.

> The Maestro suite has no in-process hook to do this, so it takes the opposite approach: it
> works *with* the seed and never asserts on counts or seeded titles. See
> [maestro/README.md](../../../maestro/README.md).

## Allure

The runner is `io.qameta.allure.android.runners.AllureAndroidJUnitRunner` with
`useTestStorageService=true` (see `app/build.gradle.kts`), configured by
`app/src/androidTest/resources/allure.properties`.

`scripts/run-espresso-with-allure.sh` copies results out of the additional-output directory
into `allure-results/`, and [`espresso.yml`](../../../.github/workflows/espresso.yml)
generates a single-file HTML report and uploads it as an artifact.

## Choosing this level over the others

| Put it here | Put it elsewhere |
|---|---|
| view inflation, gestures, `SharedPreferences` | pure logic → `app/src/test/` (unit) |
| Room migrations and DAO behaviour | multi-screen user journeys → `maestro/` |
| anything needing a real `Context` | real Google sign-in → manual |

The suite is slower and flakier than the JVM tests by nature, so it should not grow to hold
anything a unit test could prove. See [docs/quality-gates.md](../../../docs/quality-gates.md).
