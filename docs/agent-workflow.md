# Agent workflow — real failure-and-recovery trace

This trace is based on what actually happened while bootstrapping the original QA lab. The
v2 topology simplified the roles afterward: the old QA-design pass is now part of `planner`,
and the old Maestro-specific implementation responsibility now belongs to the broader
`android-test-engineer` role. The failure itself and the evidence are unchanged.

**Requirement:** add a Maestro smoke test proving bottom navigation reaches Home, Stats and
Settings.

## 1. Planner — requirement, risk and test strategy

Repository inspection found three important facts before YAML was written:

- navigation controls had stable ids;
- a login sheet appears on signed-out cold launch;
- debug builds seed about 100 demo tasks asynchronously.

The planner selected `MAESTRO` because the requirement was specifically the real nav graph +
fragment inflation + rendered destinations. A JVM test could not prove that combination.
It also explicitly left real Google sign-in and visual polish out of automation.

Acceptance criteria included reaching each destination, returning Home, destination-specific
assertions, no coordinate taps and no fixed sleeps.

## 2. Human plan approval

The approach was reviewed before implementation. This is the cheapest point to reject wrong
scope or a wrong test level.

## 3. Android test engineer — first implementation

The implementation created the navigation flow, reused common launch setup and passed:

```bash
maestro check-syntax maestro/smoke/02-navigate-bottom-nav.yaml
```

It then handed off as **ready for independent verification**, not verified.

## 4. Verifier — FAIL

Real execution failed:

```text
Assertion is false: id: profile_account_card is visible
MAESTRO FAILED (exit 1)
```

Syntax validity had proven only syntax. It had not proven the requirement.

## 5. Failure analyst — classify before repair

Investigation checked environment/app state, logcat, the resource id and then production
visibility logic. The account card existed but was intentionally hidden for guest users.
The smoke setup deliberately continued as guest.

**Classification: TEST.**

The product was correct; the assertion was wrong about the actual contract. This distinction
matters: changing a correct product to satisfy a wrong test would have been a worse bug.

Recommended test fix: assert the guest-specific profile state rather than an authenticated
account card.

## 6. Android test engineer — targeted fix

The test assertion was replaced with assertions that prove the correct guest state. This was
not "weakening until green"; it aligned the test with the requirement and made the state
assertion more precise.

## 7. Verifier — PASS

The verifier reran the flow, checked the diff and acceptance criteria, then issued PASS only
after the real command returned success.

## 8. Beyond green — mutation thinking

The test was challenged by deliberately breaking the behavior and confirming it went red.
That demonstrated the assertions could detect a regression rather than merely passing on the
happy state.

## What the trace demonstrates

1. Planning includes test strategy; a second planner role was not necessary.
2. The testing role is broader than Maestro; Maestro is a skill/tool chosen for this case.
3. A syntactically valid generated test can still be wrong.
4. Implementation and final verification must have separate authority.
5. Failure classification before repair prevents "fixing" the wrong layer.
6. Green is evidence only when the test would fail on the regression it claims to detect.
