# Mediated payout finalization

## Scope

This specification defines when a trader's node publishes the mediated payout after the mediator
proposed a result, and what the trader can still do after accepting. It applies while the
mediation result is proposed (dispute state MEDIATION_CLOSED) and the payout is not published.

## Rules

- Accepting the mediation result signs the mediated payout and sends the signature to the peer.
  The signature is the acceptance: the peer can publish the payout with it, so it cannot be
  withdrawn.
- A node that has signed the mediated payout and has the peer's signature publishes it, whichever
  of the two came last. The accept button publishes it when the peer's signature is already there,
  and the processing of the peer's signature publishes it when the trader has already accepted. A
  node that is in that state at startup publishes it then.
- After publishing, the node sends the payout to the peer and closes the disputed trade, as after
  publishing it with the accept button.
- Both nodes can publish the mediated payout at the same time. It is the same transaction on both
  sides, so this does no harm. A node therefore also accepts the peer's
  MediatedPayoutTxPublishedMessage after its own payout was published, and keeps its own payout.

Without the rule for a signature that arrives after the own acceptance, both traders could accept
before the other's signature arrived. Then both nodes have both signatures, both show that they
wait for the peer, and the payout is published only when a trader clicks accept again.

## Compatibility

No message and no persisted data change.

A node on an older version does not publish when the peer's signature arrives after its own
acceptance. If our node has the older peer's signature, our node publishes, and the older peer
completes the trade with our MediatedPayoutTxPublishedMessage as before. If the older peer publishes
with its accept button at the same moment, it rejects our message, which only logs an error there.

## Not covered

A peer's signature that is rejected because it is processed before the mediation result is known.
Detecting a payout that the peer published while the node, which has only its own signature, was
offline.
