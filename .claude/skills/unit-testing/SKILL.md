---
name: unit-testing
description: Project-specific JVM/unit testing practices for Forgetty. Use for pure Kotlin logic, mapping, repository behavior with fakes, sorting, presentation and recurrence where no Android device/runtime is required.
---

# JVM/unit testing in this repo

Location: `app/src/test/java/com/si13/app/`

Use JVM tests as the default automated level whenever they can prove the behavior. They are
faster, cheaper and more deterministic than device UI tests.

## Good UNIT candidates

- sorting and filtering rules
- recurrence/date calculations that do not require Android framework state
- mapping between domain/entity/Firestore representations
- repository behavior with fake data sources
- presentation/state calculations
- boundary and error cases in pure Kotlin

Existing examples include `TaskSorterTest`, `TaskRepeatRuleTest`, `TaskRepositoryTest`,
`TaskPresentationTest`, `TaskEntityMappingTest` and `FirestoreTaskMapperTest`.

## Rules

- Test observable behavior, not private implementation details.
- One failing assertion should identify a real contract break.
- Avoid time, locale and global-state dependence unless the test controls them explicitly.
- Use coroutine test utilities for coroutine code; do not sleep the thread.
- Prefer fakes with explicit behavior over a live Firebase/backend dependency.
- Do not duplicate a unit-proven rule in Maestro just to increase UI test count.

## Commands

```bash
./gradlew testDebugUnitTest --console=plain

# one class when Gradle filtering is appropriate
./gradlew testDebugUnitTest --tests 'com.si13.app.TaskSorterTest' --console=plain
```

Evidence lives under `app/build/test-results/testDebugUnitTest/` and
`app/build/reports/tests/testDebugUnitTest/`.
