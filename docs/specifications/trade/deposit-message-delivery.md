# Delivery of the deposit transaction message

## Scope

This specification defines when the seller of a Bisq v1 trade counts the deposit and delayed-payout
transaction message (DepositTxAndDelayedPayoutTxMessage) as delivered to the buyer, and how this
gates the publication of the deposit transaction. It applies to fiat and crypto trades, whether the
seller is maker or taker. The fiat-only prerequisite of a validated buyer account is defined in
[`fiat-buyer-payment-account-validation.md`](fiat-buyer-payment-account-validation.md).
The delivery of the payment account that the buyer sends before it acknowledges is defined in
[`buyer-payment-account-delivery.md`](buyer-payment-account-delivery.md).

## Rules

The message carries the deposit transaction, the delayed payout transaction and the seller's
payment account. The buyer needs all three: the delayed payout transaction to request arbitration,
and the seller's payment account to pay. The seller publishes the deposit transaction only after the
message is delivered, so that the buyer has them before funds are locked.

The message counts as delivered only when the buyer acknowledges it. An acknowledgement means that
the buyer has stored the deposit transaction, the delayed payout transaction and the seller's
payment account: the buyer acknowledges the message after processing it, and acknowledges a repeated
message once it has stored them, also if a later step of its first processing failed. The arrival of
the message at the buyer's node is not enough: the buyer can fail to process it, or stop before it
stored the trade, and a message that arrived directly is not kept in the mailbox.

Until the acknowledgement, the seller sends the message again: after it arrived, at the earliest
after 30 seconds, as the buyer acknowledges only after sending its own payment account to the
seller; otherwise, at the first attempt, after 4 seconds. Each interval doubles, so after an arrival
it stays at 30 seconds or more. The buyer acknowledges a repeated message again, and processes it
again only if it has not stored its data yet. After seven repetitions without acknowledgement the
seller stops sending with an error and does not publish the deposit transaction. An acknowledgement
that arrives later resumes the trade as a restart does: the seller counts the message as delivered
and evaluates the publication. A restart sends the message again. When the message is acknowledged
after the seller stopped, later or after a restart, the error of the stop is removed, also the one
an older version wrote; another error of the trade stays.

A negative acknowledgement does not count as delivery: the seller keeps sending the message again,
and the buyer acknowledges a repeated message once it has stored the data. A buyer that cannot
process the message refuses each repetition, and the seller stops after seven.

The seller stores its finalized deposit transaction before it sends the message, for fiat and crypto
trades. After a restart, a seller whose deposit transaction is not published yet restores that
transaction, sends the message again if it was not acknowledged, and then evaluates the
publication.

A seller who moves the trade to the failed trades stops sending the message, and an acknowledgement
that arrives then publishes nothing, see [`failed-trade-protocol.md`](failed-trade-protocol.md).

## Compatibility

No message changes. Buyers since v1.4.0 acknowledge the message after processing it and acknowledge
a repeated message again. The deposit transaction is published one acknowledgement later than
before.

## Not covered

- A trade that an older version left with the message arrived but the deposit transaction not
  published counts the message as delivered, as the older version did, and is published at the next
  start without waiting for an acknowledgement.
- Older versions stored the finalized deposit transaction only for fiat trades. A crypto trade that
  an older version left before the publication is not resumed at a restart, as before.
- A buyer with a modified client can publish the deposit transaction it received; the seller's gate
  does not prevent that.
