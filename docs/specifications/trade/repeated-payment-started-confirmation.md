# Repeated payment started confirmation

## Scope

This specification defines what happens when the buyer of a Bisq 1 trade confirms the start of the
payment again. The first confirmation moves the trade to the phase in which the payment is sent and
sends the payment started message to the seller. The buyer confirms again when the seller has not
acknowledged the message, for example after a failed send.

## The first confirmation

The buyer can confirm the start of the payment once the deposit transaction is confirmed (phase
`DEPOSIT_CONFIRMED`), the payment account data of the seller has arrived and is of the payment rail
of the offer (see [stablecoin-payment-methods.md](../payment/stablecoin-payment-methods.md)), the
trade has no error and it is not under arbitration. The confirmation sets the state
`BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED` and sends the message.

## A repeated confirmation keeps the trade where it is

In the desktop the buyer can confirm again while the trade is in phase `FIAT_SENT` and not under
arbitration. The payment step offers it in the states `BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED`,
`BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG` and `BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG`, until the
message has arrived or is stored in the mailbox of the seller. The repeated confirmation runs the
same tasks as the first one and sends the message again. The confirmation itself does not change
the state of the trade and never moves the trade back to an earlier phase; the result of the send
changes the state as after the first confirmation.

Earlier versions set the trade back to `DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN` before sending again. A
confirmation dialog which was still open when the payout arrived could so reset a trade with a
published payout, and listeners of the phase saw the phase go back. Keeping the state also keeps
the resend at the next start: from `BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG` the node resends
the message even if a task before the send fails, which it would not do from
`BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED`.

A confirmation in another phase, for a trade with an error, or for another trade than the one for
which the dialog was opened, is refused with a message.

After each confirmation the desktop shows that it is sending. If the seller has not answered after
10 seconds, it offers to send the confirmation again. It does not wait for a change of the state,
because a repeated confirmation may not change it. After a refused confirmation it shows neither.

## The API confirms once

The API accepts the confirmation only in phase `DEPOSIT_CONFIRMED`. A later request fails with a
precondition error and changes nothing, in particular not the transaction ID and key of an XMR
payment, which the message carries as the proof of the payment. The node resends the message
itself until the seller acknowledges it: while it runs, after 15 minutes and then after doubling
intervals, up to 10 times, and at each start in the states
`BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG`, `BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG` and
`BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG`.

## Not covered

A seller whose trade is already in phase `FIAT_SENT` does not acknowledge a repeated payment started
message. If the acknowledgment of the first message got lost, the buyer gets no acknowledgment until
the payout. The node keeps resending, and the trade stays in a state of a message without
acknowledgment: `BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG`,
`BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG` or `BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG`.
In the first and the last of them the desktop keeps offering to send the confirmation again.
