# Agentic Android Engineering Lab

> **Five engineering roles, just-in-time skills, executable orchestration, enforced authority boundaries and evidence over assertion.**

## Goal

This repository uses a real Android application to demonstrate a disciplined agentic
engineering harness. AI can plan changes, implement production code, create automated tests,
diagnose failures and verify outcomes, while role boundaries and deterministic hooks prevent
the same worker from silently expanding authority.

The design is intentionally small: responsibilities become agents; technologies/procedures
become skills.

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
│   │   ├── change/                     executable /change workflow
│   │   ├── android-development/
│   │   ├── unit-testing/
│   │   ├── android-instrumented-testing/
│   │   ├── espresso-testing/
│   │   ├── maestro-testing/
│   │   ├── android-debugging/
│   │   ├── qa-risk-analysis/
│   │   ├── verification-gates/
│   │   └── ci-debugging/
│   ├── hooks/
│   │   ├── guard-agent-bash.sh         PreToolUse authority guard
│   │   └── quick-check.sh              PostToolUse file checks
│   └── settings.json
├── app/
├── maestro/
├── scripts/
│   ├── validate-harness.sh             static policy source of truth
│   └── verify-results.sh               evidence gate
├── artifacts/agent-runs/               runtime task contracts/evidence (ignored)
└── .github/workflows/
```

## Why five roles

| Role | Why it exists |
|---|---|
| `planner` | one coherent plan includes product impact, test strategy, ownership and sequence |
| `android-developer` | production implementation is a distinct authority boundary |
| `android-test-engineer` | testing is one responsibility; frameworks are tools |
| `verifier` | the writer must not grade its own work |
| `failure-analyst` | diagnosis happens before repair so red is not blindly made green |

## Executable workflow

`/change <requirement>` turns the architecture into a repeatable process:

```text
requirement
  ↓
task contract
  ↓
planner
  ↓
human approval
  ↓
TEST_FIRST | PRODUCT_FIRST | PARALLEL | TEST_ONLY | PRODUCT_ONLY
  ↓
android-developer and/or android-test-engineer
  ↓
verifier
  ├─ PASS → summary
  ├─ INCONCLUSIVE → missing evidence/human decision
  └─ FAIL → failure analyst or clear owner → repair → verifier
```

The task contract is important because custom subagents work in isolated contexts. Each role
reads the same persisted requirement/plan/approval instead of receiving a progressively lossy
chat paraphrase.

## Agent vs skill

- **Agent = who owns the work/authority.**
- **Skill = how that role performs a technology/procedure-specific task.**

Detailed test skills are not all preloaded into `android-test-engineer`. They are available
through `Skill` and loaded only when the planner-selected level needs them:

```text
UNIT                  → unit-testing
ANDROID INSTRUMENTED  → android-instrumented-testing
ESPRESSO UI           → android-instrumented-testing + espresso-testing
MAESTRO E2E           → maestro-testing
failure diagnosis     → android-debugging / ci-debugging when relevant
```

This avoids context pollution while keeping one coherent testing role.

## Guardrails

Prompt instructions are guidance; hooks enforce deterministic rules.

- Read-only roles omit `Write`/`Edit`.
- Their agent-scoped `PreToolUse` hook blocks common git/source mutation through Bash before
  execution.
- `quick-check.sh` runs after Write/Edit and blocks malformed agent/skill definitions, shell
  syntax errors, invalid Maestro syntax, hard sleeps and coordinate taps.
- `validate-harness.sh` checks topology, required files, shell/frontmatter/flow rules and stale
  workflow references. PR CI calls the same script used locally.

The Bash guard is deliberately described as policy enforcement, not a perfect shell sandbox;
its purpose is to make authority boundaries materially stronger than prompt text alone.

## Test strategy

| Level | Primary purpose |
|---|---|
| JVM unit | pure Kotlin rules, mapping, sorting, repository behavior with fakes |
| Android instrumented | Room migrations, Context/preferences/framework runtime without UI focus |
| Espresso UI | in-process Activity/Fragment/View behavior and interaction |
| Maestro E2E | small set of critical packaged-app cross-screen journeys |
| Manual/exploratory | credentials, visual judgement, hardware/device-specific concerns |

The planner chooses the lowest reliable level and also chooses implementation sequencing.
`TEST_FIRST` is preferred when a deterministic regression can define behavior; it is not
forced when UI/testability must exist first.

## Shared-file ownership

One task has one owner per file. `app/build.gradle.kts` is a typical shared boundary:
production dependencies normally belong to the developer, test dependencies to the test
engineer, but if both need the file the planner selects one owner and records the other role's
requested edit. `PARALLEL` is forbidden when shared-file ownership is unresolved.

## Evidence semantics

`scripts/verify-results.sh` uses distinct outcomes:

- `0` — full PASS, or an explicitly requested PARTIAL run with `--allow-skips`;
- `1` — FAIL;
- `3` — INCOMPLETE because required evidence was skipped/unavailable.

The word PASS is not used for missing device evidence.

## What remains next

The next maturity layer is **agent evals**, separate from app tests: scenarios measuring
whether planner selects the right level/sequence, implementers respect ownership, verifier
refuses unsupported PASS, guardrails block forbidden operations and failure analyst correctly
classifies seeded failures. Those evals should remain a separate PR so this harness change can
be reviewed independently.
