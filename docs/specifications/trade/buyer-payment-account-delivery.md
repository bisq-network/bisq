# Delivery of the buyer's payment account

## Scope

This specification defines how the buyer of a Bisq v1 trade sends its payment account
(ShareBuyerPaymentAccountMessage) to the seller after it processed the deposit and delayed-payout
transaction message, and when it counts the message as delivered. The seller's validation of the
account is defined in
[`fiat-buyer-payment-account-validation.md`](fiat-buyer-payment-account-validation.md), the delivery
of the seller's deposit message in [`deposit-message-delivery.md`](deposit-message-delivery.md).

## Rules

The buyer sends the message as a step of the processing of the deposit message, after it stored the
deposit transaction, the delayed payout transaction and the seller's payment account. The message
counts as delivered when it arrives at the seller's node, or when the seller acknowledges it. The
acknowledgement has its own message state: the state of the seller's deposit message does not count,
and an acknowledgement that the buyer got before the step started counts at once. A negative
acknowledgement does not count as delivery.

Until then the buyer sends the message again while the deposit transaction is not confirmed: the
first time after 4 seconds, and each interval doubles. A send that reports its outcome before it
returns, as one without a connection does, is repeated like any other. After seven repetitions
without delivery the buyer stops with an error. After the deposit transaction is confirmed the buyer
schedules no further send, and an acknowledgement that comes later still ends the step.

## Compatibility

No message, persisted data or protocol change. Sellers have acknowledged the message after
processing it since the message was introduced. The message state is not persisted.

## Not covered

- The arrival of the message at the seller's node counts as delivery, although the seller can fail
  to process it. A negative acknowledgement that the seller sends then is only logged, and for a
  fiat trade the seller does not publish the deposit transaction.
- After seven repetitions without delivery the buyer's trade gets an error, the buyer refuses the
  deposit message, and the final check of the delayed payout transaction does not run. The seller
  can still process the payment account from its mailbox if a copy was stored there.
- If the deposit transaction confirms before the message is delivered and no acknowledgement comes
  later, the step does not end, and the final check of the delayed payout transaction does not run.
  The buyer had already checked that the delayed payout transaction is the one it signed.
- After a restart the buyer does not send the message again. The delivery then depends on a copy in
  the seller's mailbox.
