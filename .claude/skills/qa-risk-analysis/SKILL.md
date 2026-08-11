---
name: qa-risk-analysis
description: Risk-based testing, shift-left, and shared quality ownership. Use when deciding WHAT to test, at which level, and what deliberately not to automate — before writing any test. Provides the risk dimensions to score a change against.
---

# QA risk analysis

## Three ideas this repo is built on

**Risk-based testing.** Testing effort follows risk, not code structure. A change to
recurrence-rule logic and a change to a button's corner radius do not deserve equal
attention. The output of test design is a *prioritized* set, and things at the bottom get
consciously dropped.

**Shift left.** Move each check to the earliest, cheapest, most reliable point that can
still catch the problem. A due-date formatting bug found by a JVM unit test in 2 seconds is
strictly better than the same bug found by a UI test in 3 minutes, which is strictly better
than finding it in CI, which is strictly better than a user finding it. Shift-left also
includes non-test activity: reviewing the requirement, asking about error states, and
adding a test id to a layout so it is testable at all.

**Shared quality ownership.** QA does not "own quality" — everyone who changes the product
does. The QA role owns *risk visibility, test strategy and evidence standards*. Practically:
a developer writing a feature without unit tests has not finished the feature, and a QA
engineer who finds an untestable UI should get an id added rather than write a
coordinate-tap.

## Dimensions to score a change against

Walk all ten. Most will be "low / not applicable" — recording that is itself the analysis.

| Dimension | Ask |
|---|---|
| **User impact** | If this breaks, what can the user no longer do? Is there a workaround? |
| **Business impact** | Data loss, lost trust, broken core loop, store rating, revenue? |
| **Frequency** | Is this on the path every user takes every session, or a rare corner? |
| **Technical complexity** | Async work, concurrency, date/time, migrations, caching? Complexity is where bugs live. |
| **Integration risk** | How many boundaries does it cross — Room, Firestore, Auth, notifications, widgets, system pickers? |
| **Historical risk** | Has this area broken before? `git log --oneline -- <path>` and look for "fix". |
| **Data risk** | Can it corrupt or lose user data? Migrations and the guest→cloud import are the sharp edges here. |
| **Compatibility** | minSdk 26 through current; locale, first-day-of-week, dark mode, font scale, screen size. |
| **Rollback / recovery** | If it ships broken, can it be undone? A bad Room migration cannot be. |
| **Observability** | If it broke in the field, would anyone know? Silent failures deserve more pre-release testing precisely because there is no safety net. |

## High-risk areas in this codebase

Derived from the architecture, not from guessing:

- **Room v4→v5 migration** — irreversible, touches every existing user's data. Highest risk
  in the repo. Already covered by `TaskDatabaseMigrationTest`; keep it that way.
- **Guest → authenticated task import** — data loss potential; guest rows must survive until
  the remote batch write succeeds.
- **Firestore document parsing** — old documents lack newer fields; defaults must hold.
- **Recurrence / due-date / reminder logic** — date arithmetic, time zones, DST.
- **Task sorting and filtering** — pure logic, high visibility, cheap to unit test.
- **Swipe-to-delete with undo** — destructive action, gesture-driven, easy to regress.

## Deciding the level

Ask, in order:

1. Can a **unit test** prove it? Then it is a unit test. Stop.
2. Does it need real persistence or component wiring? **Integration.**
3. Is it a contract with a backend? **API / mapper test** — prefer a mapper unit test over
   hitting a live service.
4. Is the requirement genuinely *"the user can get from A to B on the real screens"*?
   Then, and only then, **Maestro**.
5. Is the value in human judgement, or does it need real credentials or hardware?
   **Manual / exploratory.**

### The test pyramid is a budget, not a decoration

UI tests cost the most to write, the most to run, and — the part usually forgotten — the
most to *maintain* and *diagnose*. Every UI test is a standing liability against future UI
changes. Keep a handful that cover the journeys you would hot-fix a release for.

## Anti-patterns

- **Counting test cases as a quality metric.** Two hundred shallow cases with poor
  assertions are worse than fifteen sharp ones: they take longer, fail more often for
  irrelevant reasons, and train the team to ignore red.
- **Generating a case per input value** where one boundary test and one representative
  value would do.
- **UI-testing pure logic** because the UI is where it was noticed.
- **Testing the framework.** RecyclerView scrolls. Room persists. Not your job.
- **100% coverage as a goal.** Coverage measures execution, not verification. A test with no
  meaningful assertion raises coverage and catches nothing.
- **Automating everything.** Some checks are cheaper to do once by hand, forever, than to
  automate and maintain.

## What good test design output looks like

- A short prioritized list, highest risk first.
- Every item labelled with its level and a one-line justification.
- An explicit **"not automated, and why"** section. If that section is empty, no trade-offs
  were made and the design is not finished.
- Named residual risk, so gaps are decisions rather than oversights.
