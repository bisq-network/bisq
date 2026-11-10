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
- If the node refuses a dispute of an open trade because it is not addressed to this trader, and the
  node has no dispute of this type for the trade, the node tells the trader that it refused a case
  that the peer opened, so that the trader can open one.

## Rationale

The trader ID is the key of a dispute in the node's list. A dispute with another trader ID would be
stored next to the trader's own dispute and would set the dispute state of the trade again. The
opener chooses the contract that the agent copies, so without the first rule the opener could make
the agent create such a dispute. After a mediation result, the state MEDIATION_STARTED_BY_PEER would
take the trade out of MEDIATION_CLOSED, where the trader accepts the result and the node publishes
the payout, and a second result could follow
([`mediation-payout-finality.md`](mediation-payout-finality.md)).

The opener can make the agent create the peer's dispute with a key ring that the opener chose (see
Not covered). The peer's node refuses such a dispute, so the peer has no case for the trade. If that
key ring has another signature key, the agent's node ignores the peer's error, because it checks the
acknowledgement against that key ring. Only the peer's node knows about the refusal, so it tells the
trader. If the trader then opens a dispute, the agent's node has a third dispute for the trade,
refuses it as a replay and warns the agent, so the agent learns about the dispute that the opener
created.

## Compatibility

No message and no persisted data change. Agents of earlier versions create the dispute for the
other trader from the same key ring, so their disputes pass the first rule.

## Not covered

Other content checks of the dispute, for example a contract that differs from the contract of the
trade: the node logs them and stores the dispute, so that a validation problem does not block the
dispute. A refund request that the peer opens after a mediation result is allowed.

The key rings of the traders are not part of the contract data that the traders sign, so the agent
cannot check the key ring that the opener sends for the peer. With a key ring whose signature key the
opener holds, the opener can also write to the agent as the peer. Binding the key rings to the signed
contract data would remove this, but it changes the trade protocol and needs a decision of the
maintainers.
