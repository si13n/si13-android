---
name: change
description: Orchestrate a complete Android feature, bug fix or automated-test change through the five-role harness: task contract, planner, human approval, implementation sequencing, independent verification and failure routing. Use for non-trivial repository changes instead of implementing directly in the main session.
argument-hint: "[requirement, issue, or requested change]"
allowed-tools: Agent Read Write Edit Bash Skill AskUserQuestion
---

# /change — executable engineering workflow

Treat `$ARGUMENTS` as the requested change. The main Claude Code session is the orchestrator;
it should coordinate roles, not implement production/test code itself.

## 1. Create a task contract directory

Generate a run id and create an ignored evidence directory:

```bash
RUN_ID="TASK-$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "artifacts/agent-runs/$RUN_ID"
```

Write `requirement.md` with the user's request verbatim plus any explicit constraints. Record
only decisions actually made; do not invent missing requirements.

## 2. Plan

Spawn `planner`. Delegation must tell it to read `requirement.md` and the repository, then
produce its mandatory plan sections including owner routing, implementation sequence, test
strategy, verification plan, acceptance criteria and expected files.

Save the planner's returned report verbatim to `plan.md`.

## 3. Human approval gate

For any production behavior change, new/changed automated test, architecture change or
non-trivial refactor, ask the human to approve/revise the plan **before implementation**.
Save the decision and any corrections to `approval.md`.

If revised, send the corrections back to `planner`, replace `plan.md`, and ask for approval
again. Do not silently interpret a rejected plan as permission to implement.

## 4. Execute the approved sequence

Read `Implementation Sequence` from `plan.md`:

- `TEST_FIRST` — spawn `android-test-engineer` first. Preserve red evidence in its report,
  then spawn `android-developer`, then return to the test engineer only if additional test
  implementation is needed.
- `PRODUCT_FIRST` — spawn `android-developer`, then `android-test-engineer` if routed.
- `PARALLEL` — spawn both only when the plan states files are disjoint and no shared file is
  owned by both. Otherwise fall back to ordered execution and report why.
- `TEST_ONLY` — spawn only `android-test-engineer`.
- `PRODUCT_ONLY` — spawn only `android-developer`; residual/manual verification must remain in
  the contract.

Every delegation must tell the agent to read `requirement.md`, `plan.md` and `approval.md`.
Do not paraphrase away acceptance criteria or file ownership.

Save returned reports as `developer-report.md` and/or `test-report.md`.

## 5. Independent verification

Spawn `verifier` with the contract directory path. It must independently read the original
requirement, approved plan, actual diff and real evidence. Save its report as
`verification.md`.

- `PASS` → write `summary.md` with evidence locations and stop.
- `INCONCLUSIVE` → ask the human whether/how to obtain the missing evidence; never round up.
- `FAIL` with clear owner → route directly to that implementation agent.
- `FAIL` with unclear cause → spawn `failure-analyst`; save `failure-analysis.md`, then route
  the recommended fix to the correct owner.

After every repair, run `verifier` again. Do not let an implementer self-promote to PASS.

## 6. Contract discipline

The canonical runtime files are described in `docs/task-contract.md`. The contract is the
handoff boundary between isolated agent contexts. Chat summaries are convenience only.

Do not commit `artifacts/agent-runs/`; they are execution evidence and are gitignored.
