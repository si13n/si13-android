# Agentic Android Engineering Lab

> **Five engineering roles, framework-specific skills, independent verification, evidence over assertion.**

## Goal

This repository uses a real Android application to demonstrate a disciplined agentic
engineering harness. AI can plan changes, implement production code, create automated tests,
diagnose failures and verify outcomes — but authority is deliberately separated so an
implementation agent cannot approve its own work.

The v2 topology simplifies the original QA-only lab. Roles are organized by responsibility,
not by tool. Espresso and Maestro are skills used by one Android test engineer rather than
separate agent identities.

## Layout

```text
si13-android/
├── CLAUDE.md
├── .claude/
│   ├── agents/
│   │   ├── planner.md
│   │   ├── android-developer.md
│   │   ├── android-test-engineer.md
│   │   ├── verifier.md
│   │   └── failure-analyst.md
│   ├── skills/
│   │   ├── android-development/
│   │   ├── unit-testing/
│   │   ├── espresso-testing/
│   │   ├── maestro-testing/
│   │   ├── android-debugging/
│   │   ├── qa-risk-analysis/
│   │   ├── verification-gates/
│   │   └── ci-debugging/
│   ├── hooks/quick-check.sh
│   └── settings.json
├── app/
│   ├── src/main/          production Android code
│   ├── src/test/          JVM/unit tests
│   └── src/androidTest/   Espresso/instrumented/Room tests
├── maestro/               critical E2E journeys
├── scripts/               executable gates and evidence collection
├── artifacts/             run evidence
└── .github/workflows/     PR, Espresso and Maestro CI
```

## Why five roles

| Role | Why it exists |
|---|---|
| `planner` | one coherent plan should include product impact **and** test strategy |
| `android-developer` | production implementation is a distinct authority boundary |
| `android-test-engineer` | testing is one responsibility; UNIT/Espresso/Maestro are tools |
| `verifier` | the writer must not grade its own work |
| `failure-analyst` | diagnosis should happen before repair so red is not blindly "fixed" |

The design avoids two common forms of overengineering:

1. **planner + QA planner duplication** — test strategy now belongs to planning.
2. **agent per framework** — Maestro, Espresso and JVM testing are skills of the test role.

## Workflow

```text
REQUIREMENT
    ↓
PLANNER
  ├─ architecture impact
  ├─ technical + quality risk
  ├─ owner routing
  ├─ test level
  ├─ acceptance criteria
  └─ verification plan
    ↓
HUMAN APPROVAL
    ↓
┌─────────────────────────────┐
│ android-developer           │
│ android-test-engineer       │
│ (one or both, as planned)   │
└─────────────────────────────┘
    ↓
VERIFIER
  ├─ PASS → done
  ├─ INCONCLUSIVE → obtain missing evidence / human decision
  └─ FAIL
       ↓
  FAILURE-ANALYST
       ↓
  route root cause to the correct implementer
       ↓
  VERIFIER again
```

## Agent vs skill

A simple rule keeps the topology sane:

- **Agent = who owns the work and authority boundary.**
- **Skill = how that role performs a technology/procedure-specific task.**

Examples:

```text
android-developer
  └─ android-development

android-test-engineer
  ├─ unit-testing
  ├─ espresso-testing
  ├─ maestro-testing
  └─ android-debugging
```

Adding a new framework does not automatically create a new agent. A new agent is justified
only when a new independent responsibility or authority boundary appears.

## Test strategy

The planner selects the lowest reliable level. The test engineer follows that routing rather
than defaulting to UI automation.

| Level | Primary purpose |
|---|---|
| JVM unit | pure Kotlin rules, mapping, sorting, repository behavior with fakes |
| Espresso/instrumented | Android runtime, view interaction, lifecycle/prefs, Room migrations |
| Maestro | small set of critical cross-screen user journeys |
| Manual/exploratory | real credentials, visual judgement, device-specific behavior |

The repository intentionally has far more low-level checks than Maestro flows.

## Harness components already present

- **Instructions/rules:** `CLAUDE.md`, agent prompts and skills.
- **Tools/execution:** Gradle, adb, emulator, Espresso, Maestro, GitHub Actions.
- **Guardrail hook:** `.claude/hooks/quick-check.sh` performs cheap post-edit checks.
- **Deterministic gates:** `scripts/verify-results.sh` and PR workflows.
- **Evidence:** JUnit, Android reports, Allure, Maestro debug output, logcat and artifacts.
- **Failure loop:** verifier → failure analyst → correct implementation owner → verifier.

## What is deliberately next, not hidden

This topology is the foundation. The next maturity layer is **agent evaluation**, separate
from app tests: scenarios that measure whether the planner chooses the right test level,
implementers stay in scope, the verifier refuses unsupported PASS, and the failure analyst
classifies known failures correctly. Those evals should be added as a separate change so the
role refactor itself remains reviewable.
