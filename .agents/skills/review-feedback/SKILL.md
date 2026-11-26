---
name: review-feedback
description: "Evaluate claims from a Bisq 1 code reviewer and address verified findings. Use it when the user message contains feedback prefixed with `reviewer claims:` or asks to handle review comments on a change."
---

# Review Feedback

When the user provides feedback prefixed with `reviewer claims:`, or asks to handle review comments:

* Treat each claim as a hypothesis to verify, not as inherently correct.
* Check each claim against the reviewed revision, the intended behavior in [the specifications](../../../docs/specifications/README.md) and the relevant callers or tests.
* Classify each claim as correct, partially correct, incorrect, obsolete, already fixed, duplicated by another claim, or dependent on an unresolved requirement. Ignore duplicate and obsolete claims once that is established. Raise a claim that depends on an unresolved requirement with the developer instead of deciding it.
* For a valid claim, fix the underlying issue rather than mechanically applying the reviewer's proposed solution. Use the `root-cause-analysis` skill when the claim points at a symptom.
* Run a check that distinguishes the corrected behavior from the original, for example a test that fails without the fix.
* Group claims that require the same fix. Otherwise create a separate logical commit for each independent valid claim where practical, and reference the substance of the claim in the commit message so the relation between feedback and fix is clear.
* If a claim is invalid or not justified, do not change the code to satisfy the reviewer. Explain in the reply why it does not apply and cite the code path or test evidence.
* Report the revision that contains the fixes and any behavior that remains unverified, so the reviewer can recheck the result.
* Stop after all supplied claims are evaluated and all justified fixes are completed.
