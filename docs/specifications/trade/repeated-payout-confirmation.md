# Repeated payout confirmation

## Scope

This specification defines what happens when the seller of a Bisq 1 trade confirms the payment
receipt again after the payout transaction was published. The seller protocol accepts the payment
received event in that phase, so that the payout message can be sent to the buyer again. The
desktop asks the seller to do this when sending takes long, and the API accepts the same request.

## The payout transaction is created once

The seller creates and signs the payout transaction at the first confirmation, from the buyer's
signature and the payout addresses of the contract, and the trade keeps it from then on.

A repeated confirmation does not create the payout transaction again. It publishes the stored payout
transaction if the wallet does not know it as pending or confirmed, and it sends the payout message
to the buyer again. The buyer keeps its payout transaction when it already has one.

The seller's payout address entry is only needed to create the payout transaction. The desktop
releases it when it shows the completed trade in step 4, and closing a dispute releases it as well,
so creating the payout transaction again would fail although the payout is published.

## Not covered

The desktop asks for a repeated confirmation 10 seconds after the first one, while the payout
message can still be on its way. During a repeated confirmation the trade shows step 3 again until
the message arrives or is stored in the mailbox, and the hint can appear again. This specification
only ensures that the repeated confirmation does not fail.

After a mediated payout the stored payout transaction is the mediated one. If the mediation result
did not penalize the seller, the protocol still accepts a confirmation, for example through the
API. It then re-sends the mediated payout transaction and, as after a normal payout, may sign the
buyer's account age witness. Whether a confirmation should be accepted after a mediated payout is
not decided here.
