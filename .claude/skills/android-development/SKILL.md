---
name: android-development
description: Project-specific production Android engineering practices for Forgetty — architecture, ownership, persistence, Firebase/Room boundaries, testability and safe implementation workflow. Use when planning, implementing or reviewing production Android changes.
---

# Android development in Forgetty

Forgetty is a native Kotlin Android app using Activities, Fragments, XML Views, Room,
Firebase Authentication and Firestore. Production code lives under `app/src/main/`.

## Architecture facts

- `MainActivity` hosts navigation and extension/deep-link entry points.
- `HomeFragment` owns the main task presentation, search, filters, sorting and sections.
- `TaskRepository` routes between guest/local and authenticated/remote storage.
- `LocalTaskDataSource` uses Room.
- `RemoteTaskDataSource` uses Firestore.
- `AuthRepository` / Google auth classes own sign-in state.
- Room schemas are exported under `app/schemas/`; migration correctness matters to existing
  users and must not be treated as a cosmetic implementation detail.

Read the real code before relying on this summary; this skill is navigation, not a substitute
for repository inspection.

## Implementation principles

- Make the smallest change that satisfies the approved requirement.
- Preserve the current architecture unless the plan explicitly changes it.
- Separate product behavior from test behavior. Do not introduce debug-only production
  semantics just to make automation easy.
- Stable resource ids and accessibility descriptions are legitimate product testability and
  accessibility features.
- Avoid timing-based correctness. Expose/observe real state transitions.
- Keep guest/local and authenticated/Firestore behavior intentionally consistent where the
  product contract requires it.
- Never commit credentials, service secrets or fake production auth.

## Data and persistence

Room changes are high-risk. When entity/schema versions change:

1. Update the schema/version intentionally.
2. Provide a migration for existing data when required.
3. Export the schema.
4. Have the test strategy include a real migration/instrumented test.

Firestore mapping must tolerate older documents when the product already supports safe
defaults. Prefer mapper/repository tests over live-network tests for contract behavior.

## Testability

If a user-visible control has no stable id and automation needs to address it, the right fix
may be adding a real resource id or accessibility semantic — not a coordinate tap.

Do not expose internal implementation details solely for tests when an observable public state
already exists.

## Build signals

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Device-level correctness belongs to the relevant Espresso/Maestro verification path.
