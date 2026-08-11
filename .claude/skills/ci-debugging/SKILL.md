---
name: ci-debugging
description: Systematic investigation of CI failures in GitHub Actions for this Android repo — passes locally but fails in CI, Gradle issues, missing env vars, emulator startup, artifacts, timeouts, caches, and parallel test data conflicts. Use when a workflow run is red.
---

# CI debugging

Workflows: `.github/workflows/mobile-tests.yml` (build + static validation, plus an optional
emulator job) and `.github/workflows/espresso.yml` (the pre-existing Espresso + Allure run).

## Start here: what is different about CI?

When something passes locally and fails in CI, the cause is almost always one of these
differences. Check them in this order before reading any application code.

| Difference | Local | CI (`ubuntu-latest`) |
|---|---|---|
| JDK | whatever is on PATH (here: 21) | whatever `setup-java` pinned (here: 17) |
| Android SDK | full install, cached | installed per-run |
| Device | a warm emulator you already booted | cold boot, software GPU, no KVM unless enabled |
| Files | your whole working tree | only what is committed |
| Env / secrets | your shell, `local.properties` | only what the workflow declares |
| Timing | fast machine, warm caches | slower, contended, cold |
| State | leftover app data, prefs, DB rows | pristine every time |

**`local.properties` is gitignored** — a step that depends on it will fail in CI. The Android
Gradle Plugin resolves the SDK from `ANDROID_HOME` / `ANDROID_SDK_ROOT` on the runner, which
is why the build works without it.

## Investigation order

1. **Read the failing step's log from the top.** The first error matters; later errors are
   usually consequences. Scrolling to the bottom and reacting to the last line is the most
   common mistake.
2. **Which step failed** — checkout, setup, build, test, upload? That alone classifies most
   failures.
3. **Is it reproducible locally?** Try to match CI conditions:
   ```bash
   JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew clean assembleDebug --no-build-cache
   ```
   `clean` + `--no-build-cache` is the closest cheap approximation of a fresh runner.
4. **Is it deterministic in CI?** Re-run the job. Same failure → real bug. Different failure
   → timing, ordering or resource contention. **Do not "fix" it by adding a retry.**
5. **Download the artifacts.** JUnit XML, HTML reports, logcat and screenshots exist so you
   do not have to guess. Read them.

## Gradle

```bash
./gradlew assembleDebug --console=plain --stacktrace
./gradlew assembleDebug --info          # when the error message is too terse
./gradlew --status ; ./gradlew --stop   # daemon problems
```

| Symptom | Likely cause |
|---|---|
| `Unsupported class file major version` | JDK mismatch — align `setup-java` with what AGP/Kotlin need |
| `Could not resolve <dep>` | network blip, or a repo missing from `settings.gradle.kts` |
| `SDK location not found` | `local.properties` absent **and** `ANDROID_HOME` unset |
| KSP / Room errors | schema location, or `app/schemas` not committed |
| OOM / `Metaspace` | raise `org.gradle.jvmargs` in `gradle.properties` |
| Works locally, fails clean | you depend on a stale build output or an uncommitted file |

## Missing environment variables

Symptoms: a value is empty, not wrong. `null`, `""`, or "not found" where you expected
config. Check that the workflow actually passes it, that a fork PR is not silently denied
secrets (`pull_request` from a fork gets none), and that the name matches exactly.

Never work around a missing secret by hardcoding a credential. Never commit one.

## Emulator startup (the optional job)

The single most common source of CI flake in mobile. This repo therefore keeps the emulator
job **separate and opt-in**, so a device problem never blocks the build signal.

```yaml
# KVM must be enabled or boot is ~10x slower and often times out
- run: |
    echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
      | sudo tee /etc/udev/rules.d/99-kvm4all.rules
    sudo udevadm control --reload-rules
    sudo udevadm trigger --name-match=kvm
```

Boot flags that matter: `-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim
-no-snapshot-load -no-snapshot-save`. Snapshot loading is a frequent cause of "worked
yesterday" — a corrupt snapshot restores a broken device state.

| Symptom | Cause |
|---|---|
| boot timeout | KVM not enabled; timeout too short; API level image slow to fetch |
| `device offline` mid-run | emulator crashed — check the emulator log, not the test |
| black screen / GPU errors | wrong `-gpu` mode |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | AVD partition too small |
| tests pass, job still red | a later step (report upload) failed — read the whole log |

Also: **disable animations** (`disable-animations: true`) or UI tests will race the UI.

## Timeouts

- Always set `timeout-minutes` on the job. A hung job burns runner minutes silently.
- A test timing out is a *symptom* — find whether the app hung, the device died, or a wait
  had no matching condition.
- Never fix a timeout by raising it without understanding it. Raising the limit turns a
  15-minute red into a 60-minute red.

## Caches

Cache corruption presents as impossible errors: a dependency that exists but "cannot be
resolved", a stale generated class, a build that fails only on the runner.

To test the theory, disable the cache for one run (or bump the cache key). If it goes green,
you have your answer. `actions/setup-java`'s `cache: gradle` is convenient but must be
invalidated when the wrapper or the version catalog changes.

## Parallel test data conflicts

If shards or matrix jobs share state, they will interfere.

- Each Maestro flow must set up its own state (`clearState`) and must not read data another
  flow created.
- **Unique test data per run.** `run-maestro.sh` passes `MAESTRO_RUN_TAG` so created tasks
  have unique titles. Two shards creating `"Test task"` will make each other's assertions
  ambiguous.
- Shared remote state (a real Firestore project) is the hard case: prefer local/guest mode in
  automation, which is exactly why the smoke suite dismisses the login sheet with
  *Continue as guest*.
- Symptom of a data conflict: failures that move between tests when you change the order.

## Rules

- Fix the cause, not the symptom. A retry is not a fix, and `continue-on-error` on a test
  step turns your CI into decoration.
- One variable at a time.
- If the CI failure is real, it is telling you something your local run could not. That is
  CI doing its job — do not silence it.
