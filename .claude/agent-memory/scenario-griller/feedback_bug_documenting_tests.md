---
name: feedback-bug-documenting-tests
description: User wants regression tests to assert correct/expected behavior even when the app currently fails that assertion, not softened to match current buggy behavior.
metadata:
  type: feedback
---

When a scenario-grilling session surfaces a genuine bug candidate (endpoint returns the wrong status/behavior), write the test asserting the CORRECT/expected behavior — even if that means the test fails against the current app. Do not soften the assertion to match current (buggy) behavior just to make the suite green.

**Why:** the user wants the Bruno/RestAssured suite to visibly flag known bugs so they surface on every run, with the actual fix tracked separately (e.g. a Jira ticket) rather than fixed as part of the scenario-grilling pass.

**How to apply:** when discussing edge cases in step 4 of the scenario-griller workflow, if a scenario looks like undocumented/buggy behavior (not just an ambiguous-but-intentional design choice), say so explicitly and ask which status the *correct* fix should return — then write the test against that expected value. In the `docs{}` / doc-comment block, add a line stating "Currently fails: <what the app does today>" so a reader isn't confused when the test run shows red. Keep the `docs/api-scenarios.md` Status column as `implemented in <path>` regardless of pass/fail — the row tracks whether a test exists, not whether it currently passes. Contrast with ambiguous-but-not-clearly-wrong behavior (e.g. [[project-booking-system-context]] SCN-002, movie-not-found returning an empty list) — there, lock in current behavior as the assertion instead, since it's not treated as a bug.
