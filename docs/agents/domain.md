# Domain docs: single-context layout

This repo uses a **single-context** layout:

- **`CONTEXT.md`** at the repo root: the canonical source for domain terminology, business rules, and the model that code implements
- **`docs/adr/`**: architecture decision records (one file per major decision)

## When to read CONTEXT.md

Agent skills read `CONTEXT.md` automatically to ground their work in this project's domain.

You should edit it when:
- A term or concept needs clarification
- A business rule changes
- The mental model of the system shifts
- You're onboarding a new person or agent

## Architecture decisions

Major decisions go in `docs/adr/` as `.md` files, one per decision. Name them `NNNN-short-title.md` (e.g., `0001-use-kotlin-for-android.md`).

Each ADR should:
- State the decision clearly
- Explain the alternatives considered
- Record the outcome and why

Agent skills read these to understand *why* the code is shaped the way it is, not just what it does.
