# Task contract — handoff between isolated agents

## Why it exists

Subagents work in isolated contexts. A chain of conversational summaries can silently lose an
acceptance criterion, file owner, edge case or human correction. The task contract is the
canonical runtime handoff for `/change`.

Contracts are execution evidence, not source. They live under `artifacts/agent-runs/` and are
gitignored.

## Directory

```text
artifacts/agent-runs/TASK-YYYYMMDD-HHMMSS/
├── requirement.md          original request + explicit constraints
├── plan.md                 planner output
├── approval.md             human approval/corrections
├── developer-report.md     when production role ran
├── test-report.md          when test role ran
├── verification.md         latest independent verdict
├── failure-analysis.md     when diagnosis was needed
└── summary.md              written only after final PASS
```

## File rules

### `requirement.md`

Write the user's request verbatim where possible. Add only explicitly stated constraints and
resolved clarifications. Do not backfill invented requirements after implementation.

### `plan.md`

Must contain planner's mandatory sections, especially:

- Requirement/current state
- Risks
- Owner routing with one owner per file
- Implementation sequence
- Proposed changes
- Test strategy and why not a lower level
- Exact verification plan
- Numbered acceptance criteria
- Files expected to change

A revised plan replaces the unapproved plan; `approval.md` records which version/decisions the
human accepted.

### `approval.md`

Record `APPROVED` or the requested corrections. Production/test implementation must not start
until an approval exists for non-trivial changes.

### implementation reports

Reports are claims, not verdicts. They record files, commands/exit codes, coverage/criteria and
known limitations. The orchestrator saves the agents' returned reports without rewriting away
uncertainty.

### `verification.md`

Contains `PASS | FAIL | INCONCLUSIVE`, real evidence, acceptance-criterion status and scope /
ownership review. Only verifier produces final PASS.

### `failure-analysis.md`

Contains classification, observed vs inferred evidence, root cause, confidence and repair
owner. It must not edit source.

### `summary.md`

Created only after final verifier PASS. Reference evidence locations; do not replace the raw
reports.

## Ownership contract

Every expected changed file has one owner. If two roles need one shared file, planner selects
one owner and expresses the other role's requirement as an input. Parallel implementation is
allowed only when owned files are disjoint and contracts are stable.

## Sequencing contract

Planner chooses one:

- `TEST_FIRST`
- `PRODUCT_FIRST`
- `PARALLEL`
- `TEST_ONLY`
- `PRODUCT_ONLY`

The orchestrator must follow the approved mode or explicitly return to the human/planner to
change it.

## Evidence integrity

- A report must distinguish **NOT RUN** from PASS.
- Required unavailable evidence produces INCONCLUSIVE.
- Old artifacts must not be treated as evidence for a new run.
- An implementation report cannot promote itself to VERIFIED.
- Contract files should preserve uncertainty and failure evidence rather than rewriting the
  story after the fact.
