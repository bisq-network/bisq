# Peer signature of the mediated payout

## Scope

This specification defines when a trader's node checks the peer's signature of the mediated payout
(MediatedPayoutTxSignatureMessage), and what it does with a signature that arrives before its own
copy of the mediation result.

## Rules

- The node checks the peer's signature on the payout transaction that pays the amounts of the
  mediation result, which this trader received from the mediator, to the payout addresses of both
  traders. It stores the signature only if it is valid for that transaction.
- Before the mediation result is applied, the signature cannot be checked, so the node does not
  reject it. It keeps the message and checks it once the result is applied (dispute state
  MEDIATION_CLOSED). Before the result means no dispute yet, or a mediation without result
  (MEDIATION_REQUESTED or MEDIATION_STARTED_BY_PEER).
- The node keeps one such message per trade. A newer one replaces it. If a signature of the peer
  passes the check before the kept message is handled, the kept message is dropped.
- The node acknowledges the message after the check, with its outcome. A dropped message is not
  acknowledged.

The peer signs only after it received the mediation result, but this trader's copy can arrive
later. The mediator sends the result to each trader separately, and after a restart the trade
protocol can handle its mailbox messages before the support messages, such as the mediation result,
are applied. Without the rule for an early signature, the signature is checked against payout
amounts that are not set yet, it is rejected, and the trade shows an error.

## Compatibility

No message and no persisted data change. A node on an older version still rejects a signature that
arrives before the result.

## Not covered

A kept message is not persisted. One from the mailbox stays in the mailbox until its check
succeeds or it is dropped, so it is handled again after a restart. One received directly is lost
if the node stops before the result arrives; the peer then needs this trader's signature to
publish the payout.
