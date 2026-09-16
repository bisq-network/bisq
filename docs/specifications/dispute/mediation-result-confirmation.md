# Mediation result confirmation

## Required behavior

Accepting or rejecting a displayed mediation result must apply only while that result
remains the current result for the dispute. Receiving another authenticated mediator result
replaces the current result; an action from an older display must not accept or reject the
replacement, initiate payout signing, or persist either decision.

Check this when the trader clicks Accept or Reject. The old display may remain open after
a replacement arrives. On a stale action, close that display and explain that the mediator
sent an updated result and the action was not applied. After the trader dismisses the error,
show the latest result for fresh review, provided the trade view is still active and the
trade remains eligible for mediation-result review. Do not automatically retry the action.

Read the latest result when reopening: another result may arrive while the error is shown.
If that result is replaced again before the next action, fail that action in the same way.
Repeated callbacks from the old popup must not create multiple error/reopen sequences or
dismiss a newer popup. Leaving the view or entering arbitration cancels pending reopening.

A newly received result invalidates the previous display even if its payout amounts are
identical. This conservative behavior avoids guessing whether a replacement is meaningful.
An unchanged result retains the existing acceptance and rejection behavior.

## Scope and compatibility

This rule concerns trader actions on the desktop mediation-result popup. It does not change
mediator authentication, payout allocation rules, arbitration actions, network messages,
persisted formats, or existing signatures. Validation and the decision must execute together
without deferring the decision beyond the validation. Incoming results replace the result
object; this rule does not introduce an in-place result-editing mechanism.
