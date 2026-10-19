# Delivery of the deposit transaction message

## Scope

This specification defines when the seller of a Bisq v1 trade counts the deposit and delayed-payout
transaction message (DepositTxAndDelayedPayoutTxMessage) as delivered to the buyer, and how this
gates the publication of the deposit transaction. It applies to fiat and crypto trades, whether the
seller is maker or taker. The fiat-only prerequisite of a validated buyer account is defined in
[`fiat-buyer-payment-account-validation.md`](fiat-buyer-payment-account-validation.md).

## Rules

The message carries the deposit transaction, the delayed payout transaction and the seller's
payment account. The buyer needs all three: the delayed payout transaction to request arbitration,
and the seller's payment account to pay. The seller publishes the deposit transaction only after the
message is delivered, so that the buyer has them before funds are locked.

The message counts as delivered only when the buyer acknowledges it. The buyer sends the
acknowledgement after processing and storing the message. The arrival of the message at the buyer's
node is not enough: the buyer can fail to process it, or stop before it stored the trade, and a
message that arrived directly is not kept in the mailbox.

Until the acknowledgement, the seller sends the message again: after it arrived, at the earliest
after 30 seconds, as the buyer acknowledges only after sending its own payment account to the
seller; otherwise, at the first attempt, after 4 seconds. Each interval doubles, so after an arrival
it stays at 30 seconds or more. The buyer acknowledges a repeated message again, and processes it
again only if it has not stored its data yet. After seven repetitions without acknowledgement the
seller stops with an error and does not publish the deposit transaction. The buyer's trade then
waits for a deposit transaction that the seller publishes only after a restart and a new
acknowledgement.

A negative acknowledgement does not count as delivery; the seller keeps sending the message again.

After a restart, a seller whose deposit transaction is not published yet sends the message again if
it was not acknowledged, and then evaluates the publication. This applies to fiat and crypto trades.

A seller who moves the trade to the failed trades stops sending the message, and an acknowledgement
that arrives then publishes nothing, see [`failed-trade-protocol.md`](failed-trade-protocol.md).

## Compatibility

No message changes. Buyers since v1.4.0 acknowledge the message after processing it and acknowledge
a repeated message again. The deposit transaction is published one acknowledgement later than
before.

## Not covered

- An acknowledgement that arrives after the seller stopped with an error is not applied; a restart
  of the seller sends the message again.
- A trade that an older version left with the message arrived but the deposit transaction not
  published counts the message as delivered, as the older version did, and is published at the next
  start without waiting for an acknowledgement.
- A buyer with a modified client can publish the deposit transaction it received; the seller's gate
  does not prevent that.
