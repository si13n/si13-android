# Agent workflow — a real end-to-end example

This is not a hypothetical. It is what actually happened while bootstrapping this repo, kept
because a real trace is more convincing than an invented one.

**Requirement:** *"Add a Maestro smoke test proving the app's bottom navigation reaches
Home, Stats and Settings."*

---

## 1. Planner (read-only)

Inspected the repo before proposing anything, and found the things that would have made a
naive test flaky:

- `app/src/main/res/layout/activity_main.xml` — nav buttons have stable ids: `homeFragment`,
  `statsFragment`, `profileFragment`, `add_task_fab`.
- `MainActivity.kt:60` — `LoginBottomSheet` is shown on every cold launch while signed out.
- `MainActivity.kt:87` — `seedDebugTasksIfNeeded()` seeds ~100 demo tasks asynchronously.

**Risks named:** the login sheet blocks every flow; the async seed makes any count-based
assertion a race; a tab can highlight while its fragment fails to inflate.

**Test level chosen:** `MAESTRO`. Justified because a broken nav graph or a fragment that
fails to inflate is only observable end-to-end on a device — no JVM unit test covers
`NavController` plus fragment inflation plus the real view tree.

**Acceptance criteria:**
1. The flow launches from a clean state and reaches Home.
2. Tapping Stats shows the Stats destination.
3. Tapping Settings shows the Profile destination.
4. Tapping Home returns to Home.
5. Each assertion targets a destination-specific view id, not the tab label.
6. No hard sleeps and no coordinate taps.

---

## 2. QA test designer (read-only)

Confirmed the level and set the boundary of what this flow does **not** own:

| Scenario | Level | Why |
|---|---|---|
| Nav reaches all three destinations | `MAESTRO` | needs the real nav graph and fragment inflation |
| Tab highlight state | `UNIT`/skip | cosmetic; not worth a UI test |
| Profile guest vs signed-in content | `MAESTRO` (this flow) | cheap to fold in, and it is a real state contract |
| Google sign-in | `MANUAL` | needs live credentials; automating it means storing them |

---

## 3. Plan approval (the human gate)

The plan was reviewed *before* any YAML existed. This is the cheapest possible place to
catch a wrong approach — an argument about a table costs minutes, an argument about a
finished suite costs days.

---

## 4. Implementer

Wrote `maestro/smoke/02-navigate-bottom-nav.yaml`, reusing
`maestro/common/launch-fresh.yaml` for the login-sheet dismissal.

Ran `maestro check-syntax` (OK) and reported:

> Implementation complete and ready for independent verification.

Note the wording. It did **not** say "done" or "working".

---

## 5. Verifier — first pass: **FAIL**

Ran the flow for real instead of trusting the syntax check:

```
scripts/run-maestro.sh maestro/smoke/02-navigate-bottom-nav.yaml
[Failed] (30s) Assertion is false: id: profile_account_card is visible
MAESTRO FAILED (exit 1)
```

**VERDICT: FAIL.** Criterion 3 unmet.

This is the point of the whole design. A flow that passes `check-syntax` and looks correct
was still wrong, and the agent that wrote it believed it was right.

---

## 6. Failure analyst — investigation, no code changes

Worked outside-in rather than editing the selector:

1. **Crash?** `grep -icE "fatal exception|androidruntime" logcat.txt` → 1 hit. Read it:
   `AndroidRuntime: VM exiting with result code 0` — benign. **Not a crash.**
   *(Side effect: this false positive was a real defect in `collect-logcat.sh`'s grep
   pattern. It was tightened, because a crash detector that cries wolf gets ignored.)*
2. **Does the id exist?** Yes — `fragment_profile.xml:38`.
3. **Is it conditionally hidden?** `grep -n "isVisible" ProfileFragment.kt` →
   line 137: `accountCard.isVisible = state.showProfileCard`
   and `ProfileViewModel.kt:26`: `showProfileCard get() = user != null`.

**Classification: `TEST`** — not `LOCATOR`, not `PRODUCT`.

**Root cause:** the smoke suite deliberately continues *as a guest*, so `user == null` and
the account card is **correctly** hidden. The app is right. The assertion asserted behaviour
the requirement never specified.

**Confidence: HIGH** — the conditional is two lines of source, not a theory.

**Recommended fix:** assert the actual guest contract instead —
`profile_guest_container` visible (`ProfileFragment.kt:139`), `profile_account_card` **not**
visible, plus the unconditional `profile_title`.

---

## 7. Implementer — the fix

Applied exactly that.

The important part: this is **not** weakening an assertion to get green. The original
assertion was factually wrong about the specification, and the replacement is **stronger** —
it now proves the Profile screen rendered the *correct guest state*, where before it only
checked that a card existed.

Had the app been at fault, the correct outcome would have been a bug report, not a test edit.
That distinction is the difference between testing and decorating.

---

## 8. Verifier — second pass: **PASS**

```
scripts/run-maestro.sh maestro/smoke/02-navigate-bottom-nav.yaml
[Passed] Bottom navigation moves between Home, Stats and Settings (21s)
MAESTRO PASSED (exit 0)
```

All six acceptance criteria met, evidence attached, diff limited to the one expected file.

---

## 9. Beyond green — does the test actually prove the requirement?

A green test is not automatically a correct test, so the suite's key assertion was
**mutation tested**: break the feature on purpose, and confirm the test notices.

| Mutation | Expected | Actual |
|---|---|---|
| A: tap `add_task_close` (cancel) instead of `add_task_button` (save) | flow must FAIL | FAILED, exit 1 |
| B: search for a task that was never created | flow must FAIL at the search assertion | FAILED: `search_results is visible` |

Mutation A also exposed a genuine fragility: it failed at
`assertVisible: home_header` — an incidental step racing the sheet-dismissal animation.
That was converted to `extendedWaitUntil` on the same condition. A wait on a condition, not
a sleep.

**Only after both mutations went red was the suite treated as evidence of anything.**

---

## What this trace demonstrates

1. The implementer produced plausible, syntactically valid, **wrong** code.
2. It said "ready for independent verification", not "done".
3. An independent verifier caught it by *executing* rather than reading.
4. Diagnosis was separated from repair, so the fix addressed the cause.
5. The failure was correctly classified `TEST` rather than the tempting `LOCATOR` — and the
   product was exonerated on evidence.
6. The fix made the assertion stronger, not weaker.
7. Green was not trusted until mutation testing proved the test could fail.
8. Two incidental defects were found along the way (the logcat false positive, the animation
   race) because someone was actually looking.
