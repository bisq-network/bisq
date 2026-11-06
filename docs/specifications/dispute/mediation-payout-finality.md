# Mediation payout finality

## Scope

This specification defines when the payout of a BISQ 1 mediation can no longer change. The payout of
a mediation result is the pair of amounts that the mediated payout transaction pays to the buyer and
to the seller. The payout addresses come from the contract and do not depend on the result.

## Rules for the mediator

- The payout of a trade is final once the mediator has sent a result for the trade to one trader.
  Every later result for the trade has the same payout: the result for the other trader, a result
  that the mediator sends to the same trader again, and a result that the mediator sends after the
  tickets were closed and reopened.
- A result counts as sent once the mediator's node has created the chat message of its
  DisputeResultMessage. A result that the mediator only prepared, for example in a window that the
  mediator closed with Cancel, is not sent. The node stores a prepared result without a chat
  message, so the result is still not sent after a restart.
- The mediator's node refuses to send a result whose payout differs from a result that was sent for
  the trade. Sending the same payout again stays possible, for example when the first message could
  not be delivered.
- The mediator's window fills the payout and the reason from the result that fixes the payout, and
  does not let the mediator change them: the result sent for the ticket itself, or else the first
  result sent for the other ticket of the trade. The summary notes stay editable. If results with
  different payouts were sent for the trade by an earlier version, the window refuses to send
  another result. The window locks the payout and the reason when the mediator sends the result,
  so they stay locked if sending fails and the window stays open.

## Rules for the trader

- A result message must contain the chat message of the result, which the agent's node creates
  before it sends the result. The node drops a result message without it when it reads the message.
  This also applies to the result messages of the refund agent.
- The first mediation result that a trader's node receives for a trade fixes the payout of the trade
  for this node.
- A later result with the same payout is a copy. The node handles it as before: it adds the
  mediator's message to the chat and replaces the stored result.
- A later result with another payout is a failure, not a correction, because the mediator cannot
  change the payout. The node does not change the dispute, the trade or the stored amounts. It adds
  a system message to the mediation chat that tells the trader that the result was refused and that
  the first result stays valid, and it acknowledges the message to the mediator with an error. This
  applies to every mediation dispute that the node has for the trade.
- After the first result, a trader's node accepts no new mediation dispute for the trade
  ([`peer-opened-dispute.md`](peer-opened-dispute.md)), so a new mediation cannot reopen the
  mediation either.

## Rationale

Each trader signs the mediated payout of the result that it received, and a trader's node signs it
only once ([`../trade/mediated-payout-finalization.md`](../trade/mediated-payout-finalization.md)).
Two different payouts for one trade cannot both complete: the two signatures do not combine to a
valid transaction, and a trader who has accepted cannot accept a changed result. A changed payout
after a result was sent therefore leaves the trade stuck, or lets a trader be asked to sign a payout
that differs from the one the other trader accepted. A fixed payout also lets the traders treat a
result with another payout as a failure instead of as a correction.

## Compatibility

No message and no persisted data change. A result that a mediator sent with another version is
treated like a result sent with this version. A trader's node of an earlier version still accepts a
result with another payout; a mediator with this version does not send one. A result that replaced
another one before the trader's node had this version stays the stored result, and the node compares
later results with it.

Earlier versions read a stored result without a chat message back with an empty chat message, which
has no ID, and stored the empty chat message with the result again. The node reads a chat message
without an ID as absent, so a result that the mediator prepared with an earlier version is not sent.
A chat message that a node creates always has an ID. Earlier versions also applied a result message
without a chat message, with an empty chat message. Nodes of all versions send the result with its
chat message.

## Not covered

The payout of the first result: the mediator decides it. The results of the refund agent. A stored
result whose payout a mediator changed after sending it, which earlier versions of the mediator's
window allowed: the node takes the stored result as the sent one.
