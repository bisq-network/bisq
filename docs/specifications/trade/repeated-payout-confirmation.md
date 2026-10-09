# Repeated payout confirmation

## Scope

This specification defines what happens when the seller of a BISQ 1 trade confirms the payment
receipt again after the payout transaction was published, and until when the seller can confirm it
after a mediation. The seller protocol accepts the payment received event after the payout was
published, so that the payout message can be sent to the buyer again. The desktop asks the seller to
do this when sending takes long, and the API accepts the same request.

## The payout transaction is created once

The seller creates and signs the payout transaction at the first confirmation, from the buyer's
signature and the payout addresses of the contract, and the trade keeps it from then on.

A repeated confirmation does not create the payout transaction again. It publishes the stored payout
transaction if the wallet does not know it as pending or confirmed, and it sends the payout message
to the buyer again. The buyer keeps its payout transaction when it already has one.

The seller's payout address entry is only needed to create the payout transaction. The desktop
releases it when it shows the completed trade in step 4, and closing a dispute releases it as well,
so creating the payout transaction again would fail although the payout is published.

## Confirmation after mediation

When the mediator proposes a result that does not penalize the seller, the seller can still confirm
the payment receipt and complete the trade with the normal payout. This also applies after the
seller accepted the proposal, as long as the mediated payout is not published.

The mediated payout counts as published when the seller's node published it, received it from the
buyer, or saw it in the network. From then on the seller cannot confirm the payment receipt any
more. The mediated payout completes the trade, so there is no payout message to send again. A
confirmation would also sign the buyer's account age witness (see
[signed-witness-admission.md](../account/signed-witness-admission.md)), which the mediated payout
does not do.

## Not covered

The desktop asks for a repeated confirmation 10 seconds after the first one, while the payout
message can still be on its way. During a repeated confirmation the trade shows step 3 again until
the message arrives or is stored in the mailbox, and the hint can appear again. This specification
only ensures that the repeated confirmation does not fail.

If the seller's node finalized the mediated payout but could not publish it, the trade keeps that
transaction as its payout. A confirmation then publishes and sends it as after a normal payout, and
can sign the buyer's account age witness. Both traders signed that transaction, so the payout
matches the mediation result.

The API does not report a refused confirmation to the caller. This applies to every reason for
refusing it.
