# Dispute opened by the peer

## Scope

When a trader opens a mediation or a refund request, the agent creates a dispute for the other
trader from the contract that the opener sent, and sends it to that trader in a
PeerOpenedDisputeMessage. This specification defines which of these disputes a trader's node
accepts.

## Rules

- The node accepts the dispute only if it is addressed to this trader: the trader key ring of the
  dispute is the node's own key ring, and the trader ID is the hash code of that key ring, as for
  the disputes that the node opens itself.
- The node accepts a new mediation dispute only while the trade has no mediation result and is not
  in arbitration, that is in dispute state NO_DISPUTE, MEDIATION_REQUESTED or
  MEDIATION_STARTED_BY_PEER. A dispute that the node already has is a copy and is handled as before.
- The node checks both rules before it moves a closed or failed trade back to the open trades.
- A refused dispute is not stored and does not change the trade. The node acknowledges the message
  and its first chat message with an error.

## Rationale

The trader ID is the key of a dispute in the node's list. A dispute with another trader ID would be
stored next to the trader's own dispute and would set the dispute state of the trade again. The
opener chooses the contract that the agent copies, so without the first rule the opener could make
the agent create such a dispute. After a mediation result, the state MEDIATION_STARTED_BY_PEER would
take the trade out of MEDIATION_CLOSED, where the trader accepts the result and the node publishes
the payout, and a second result could follow
([`mediation-payout-finality.md`](mediation-payout-finality.md)).

## Compatibility

No message and no persisted data change. Agents of earlier versions create the dispute for the
other trader from the same key ring, so their disputes pass the first rule.

## Not covered

Other content checks of the dispute, for example a contract that differs from the contract of the
trade: the node logs them and stores the dispute, so that a validation problem does not block the
dispute. A refund request that the peer opens after a mediation result is allowed.
