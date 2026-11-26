---
name: root-cause-analysis
description: "Root-cause procedure for nontrivial Bisq 1 bugs, unexpected behavior, domain inconsistencies, security-sensitive changes and problems with several edge cases. Use it before patching a symptom, before choosing between a root-cause fix and a mitigation, when presenting options to the developer, when a defect may be more severe than reported, when several explanations fit, when a specification or feature may itself be the cause, and when verifying a root-cause fix."
---

# Root Cause Analysis

For nontrivial bugs, unexpected behavior, domain inconsistencies, security-sensitive changes and problems involving several edge cases, do not start by patching the observed symptom. First determine which underlying condition makes the observed behavior possible.

## Analyze Before Fixing

Before changing production code:

* Reconstruct the execution and data flow far enough upstream to see where the problematic state or behavior originates.
* Identify the domain invariants that should hold and find where the first invariant is violated. Check the intended rule in [the specification index](../../../docs/specifications/README.md), its callers and the tests.
* Distinguish the observed symptom, intermediate consequences, defensive or edge-case handling triggered by those consequences, and the underlying defect that created the invalid state.
* Ask repeatedly: **"Why can this state exist?"** until reaching the earliest actionable cause, not the first location where the problem becomes visible.
* Inspect callers, state creation, validation, persistence, serialization and downstream consumers when relevant. Do not assume the defect is where the exception, failing test or incorrect result appears.
* Check whether existing edge-case handling, compensating logic, retries, special cases or validation exist because of an earlier defect.
* Search for other code paths affected by the same underlying condition.

Do not implement a fix until you can answer these questions, and communicate the relevant answers to the developer:

1. What is the incorrect behavior?
2. Which invariant or intended behavior is violated?
3. Where is the invalid state or decision first introduced?
4. Why is it possible for that to happen?
5. Which other behaviors are consequences of the same cause?
6. Which code becomes unnecessary if the root cause is corrected?
7. Could the proposed fix leave another path to the same invalid state?

## Question the Specification and the Feature

The specification, a feature or a design decision can itself be the root cause. If the specification permits the failure, identify the feature or assumption responsible and the consequences of changing it.

* Specifications and earlier decisions may have been written without full attention, also by AI tools. Their authority stays, but question them when they make a defect possible.
* When a rarely needed feature is the source of a class of defects, say so and ask whether it is required. Present the pros and cons of removing or restricting it next to the patch. Patching each resulting issue alone misses the question whether the feature is worth the attack surface it creates.

## Explain the Root Cause Explicitly

* For every issue investigated, reviewed or fixed, state the identified root cause and how it produces the observed behavior. Scale the explanation to the complexity and impact of the issue.
* Distinguish confirmed causes from hypotheses. Support the explanation with evidence such as the execution path, state transition, violated invariant or ownership decision.
* If the root cause is unknown or only partly understood, say so. State what is established, what is uncertain and which investigation would resolve it. Never invent a cause to satisfy this requirement.
* Do not describe only the exception, missing check or failing operation when these are consequences of an earlier defect. Explain why the invalid state was allowed to exist and which component should have prevented it.
* State whether the change removes the cause, mitigates its consequences or both. Identify any remaining paths to the same failure.
* Put the causal explanation in the findings or the final response, also when the correction is outside the authorized scope. Do not leave it only in reasoning, code comments or a commit message.

## Prefer Removal of Causes Over Addition of Handling

Prefer fixes that restore the correct invariant and make invalid states impossible or substantially harder to create. When a root-cause correction is feasible and architecturally sound, recommend it explicitly. Do not default to a symptom-level patch because it is easier, changes fewer lines or matches the initial suggestion. Implement within the authorized scope; use the effort and risk assessment below when the sound correction materially expands that scope.

When correcting the root cause:

* Re-evaluate downstream edge-case handling that existed only because of the bug, and remove obsolete workarounds, compensating logic, special cases and defensive branches.
* Do not preserve complexity merely because it exists, and do not add a branch if correcting an earlier invariant makes it unnecessary.
* Prefer reducing the number of possible invalid states over handling each invalid state separately.
* Enforce correctness at the earliest appropriate trust or state-transition boundary.
* Remove defensive handling only after establishing that it is obsolete. Keep checks needed for independent trust boundaries, previously persisted invalid data, backward compatibility or other valid failure modes.

A good fix often makes code simpler, because behavior that was treated as an edge case becomes impossible.

## Fix the Step That Creates the Problem, Not the Step That Detects It

The code that detects a problem (a validator, a verification rule, a failing test, an exception) is usually not the code that creates it. Identify the earlier step that makes the invalid state or the unwanted capability possible, and correct that step. Examples:

* A third party can change a field of a signed message without invalidating the signature. Cause: the signed data does not include the field. Fix: include it. A consistency check against another field, an extra verification rule or a documented risk only compensates for the gap.
* Two representations of one value can disagree. Cause: one is not derived from the other when it is created. Fix: derive one from the other, or remove one. Use a consistency check only when this is not possible.
* An invalid state can be persisted or relayed. Cause: the state transition that creates it accepts it. Fix the transition, not every reader.

Do not redefine the root cause to fit a convenient fix. The root cause is the earliest step at which the problem becomes possible. A check, rule or document at a later step is a mitigation and must be labeled as one.

## Designs Introduced by the Change Are Not Constraints

* A format, message, field, schema, protocol rule or API that the change under review, or unreleased commits of the current branch, introduce can be corrected without compatibility cost. Before using compatibility as an argument, verify whether the published release contains the affected design, for example by comparing with the release tag on the public repository.
* Do not limit fixes of an unreleased design to changes that keep its output identical. If the design is incomplete, complete it.
* When a review finds a gap in a new design, correct the design first. Then remove downstream checks, rules, test changes and documentation that the corrected design makes unnecessary.

## Effort, Risk and Alternatives

When correcting the root cause requires considerable effort or introduces material risk, make the tradeoff explicit before expanding the work.

* Describe the concrete changes required and why they are necessary. Identify affected domains, responsibilities, dependencies, public APIs, protocols, persisted data, DAO consensus and operational behavior where relevant.
* Explain the sources of effort and uncertainty: investigation, implementation, migration, testing and rollout. Label estimates as estimates; do not substitute "too complex" or "too risky" for analysis.
* Compare feasible alternatives: direct root-cause correction, a staged correction and a narrower mitigation where applicable. Do not invent alternatives to fill a list.
* For each meaningful alternative, state which causes it eliminates, which risks remain, its architectural effects, implementation and verification effort, compatibility implications and reversibility. Weigh the risk of leaving the defect in place against the risk of changing the code.
* Recommend one approach and explain why its benefits outweigh its costs under the current constraints. Identify any unresolved decision that materially affects the recommendation.
* If a narrower mitigation is selected, label it accurately. Explain the remaining defect, why the mitigation is appropriate and which follow-up would remove the cause. Do not describe symptom suppression as a complete root-cause fix.
* When useful, propose a staged path that reduces immediate risk while keeping a clear route to the sound design. Do not make the mitigation depend on the invalid behavior or add coupling that makes the eventual correction harder.

Neither the smallest diff nor the broadest redesign is automatically preferable. Choose the smallest sound solution with an explicit account of architectural integrity, residual risk and practical feasibility.

## Alternatives and Developer Decisions

* Complete the analysis before asking the developer to choose. The options must include the correction at the origin of the problem, even if it is larger or changes an unreleased format. If all options only change downstream handling, the set is incomplete.
* For each option, state whether it removes the cause, mitigates consequences or only documents a risk.
* Treat a developer decision as a set of required properties (for example "one signature verification per request" or "the legacy signature may be empty"), not as a restriction to the options offered. If a design found later meets those properties and removes the risk, present it.

## Challenge the Initial Problem Statement

Treat the reported issue, failing test, reviewer comment, exception or requested change as evidence of a problem, not necessarily as an accurate description of its cause. Before implementing the obvious fix:

* Check whether the reported issue is a consequence of a more fundamental defect.
* Check whether several reported edge cases share one cause.
* Check whether behavior that appears intentional compensates for a historical bug.
* Check whether fixing the immediate problem would conceal a more severe domain, security, accounting, DAO consensus or state-integrity issue.
* Do not assume the requested scope matches the true scope of the defect.

If investigation reveals a more fundamental issue, understand and correct it before adding handling for its consequences.

## Severity and Fundamental Defects

Actively look for defects with larger impact than the reported problem. Treat potential violations of core domain invariants, security properties, DAO consensus rules, accounting correctness, ownership, authorization, state integrity or irreversible state transitions as potentially severe, even when the observed symptom appears minor.

When such a defect is found:

* Stop treating the original symptom as the primary problem, and determine the scope and consequences of the fundamental defect.
* Identify all behavior and edge-case handling derived from it.
* Raise it clearly to the developer before proceeding with a narrow patch if the correction materially changes the task.
* Recommend fixing the fundamental defect first when downstream fixes would otherwise encode or preserve incorrect behavior.
* After fixing it, re-evaluate whether the original requested change is still necessary.

Judge severity by the violated invariant and its possible consequences, not by how often the bug is triggered, how large the failing diff is, whether tests cover it or whether the current task mentions it. For a suspected vulnerability, follow [the security policy](../../../SECURITY.md).

## Evaluate Competing Explanations

Do not settle on the first plausible explanation. For nontrivial problems:

* Form at least one alternative hypothesis when practical, and look for evidence that would disprove the leading one.
* Prefer explanations that account for all observed behavior with fewer special cases.
* If a proposed fix handles one example but does not explain why the invalid state exists, continue investigating.
* If branches or exceptions keep accumulating during implementation, the abstraction or the analysis may be wrong. Reconsider the underlying model.

## Residual Risks

* A documented residual risk is not a fix. Before reporting one, state why its cause cannot be removed now, what would remove it and who has to decide.
* Do not describe a removable weakness as an accepted limitation in a specification. A specification records only limitations whose cause cannot be removed at this time, together with the reason.

## Verify the Root-Cause Fix

Verification must show more than that the observed symptom is gone. Where practical:

* Add or update tests around the restored invariant, not only around the reported example.
* Verify that alternative paths cannot recreate the invalid state.
* Check whether downstream workarounds can now be removed, and search for similar assumptions or duplicated handling elsewhere.
* Re-run tests for related domain behavior, not only the originally failing test. Select them with [the testing guide](../../../docs/testing.md).
* Confirm that the fix does not merely move the failure to another layer.

For important domain or security issues, summarize the causal chain in the discussion or commit body:

**root cause -> invalid state -> downstream consequences -> observed symptom -> fix**

The fix should shorten this chain by eliminating the earliest practical cause, not lengthen it with another downstream workaround.

## Self-Check Before Reporting Fixes

Answer these questions for every fix:

1. Which earlier step made the problem possible, and does the fix change that step?
2. Is the fix a check, rule, test change or document that compensates for a gap that could be closed at its origin?
3. Were similar gaps (other fields, other message types, similar code paths) fixed the same way? If the same kind of gap received different kinds of fixes, the analysis is incomplete.
4. Would each added check or rule still be needed if the cause were corrected? If not, correct the cause instead.
5. Can a design change remove any residual risk in the report?

If most fixes are mitigations, test adaptations or documented risks, repeat the analysis before reporting.
