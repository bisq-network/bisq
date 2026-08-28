# Deposit transaction liveness

## Scope

This specification defines when block-explorer evidence may classify an unconfirmed deposit
transaction as no longer confirmable, and when that classification may authorize moving the trade
to failed trades. It covers the checks run from the deposit-confirmation step of the pending
trades view. It does not cover fee validation, which shares the lookup machinery but fails closed
on its own rules, nor purely informational status displays such as the dispute summary's payout
transaction status, which derive no verdict and gate no action.

## Evidence rule

A deposit transaction is treated as unknown to the network only when every configured explorer
provider positively answered that it does not know the transaction. The providers are queried in
rotation; a transport failure, a malformed answer or an unreachable provider is no information and
must neither produce nor revoke a verdict. A single provider returning any status for the
transaction, confirmed or not, proves it is not gone and revokes an earlier verdict.

The lookup starts only after the trade has been unconfirmed for 24 hours. That threshold is a
trigger heuristic to avoid querying explorers for young trades; it is not evidence, because it
measures the take-offer time, not the transaction's publication.

## Publication evidence

A unanimous not-found answer only means the transaction is dead if the transaction was actually
broadcast. The seller publishes the deposit transaction himself, so reaching the
deposit-confirmation step implies a broadcast on his side. The buyer receives the transaction in a
message which the seller sends before publishing; for fiat trades publication additionally waits
for that message's delivery (one of the two publication prerequisites in
[`fiat-buyer-payment-account-validation.md`](fiat-buyer-payment-account-validation.md)). The buyer
therefore needs positive broadcast evidence: either his wallet saw the network relay the
transaction before the message arrived, or broadcast peers were observed on the committed
transaction afterwards. Without such evidence no verdict may be derived, however the explorers
answer.

## Safety interval and re-validation

A transaction broadcast moments ago may not have reached the explorers yet, so one unanimous
not-found answer is not sufficient to act on. Moving the trade to failed trades requires a second
unanimous not-found answer, obtained by a fresh lookup at the moment the user invokes the action,
arriving at least 30 minutes after the first observation. A transport failure during that
re-validation blocks the action instead of falling back to the earlier verdict, and a provider
returning a status for the transaction cancels the verdict entirely.

## Verdict lifecycle

The verdict is transient and local to the user interface. It is not persisted; a restart
re-evaluates the situation from scratch. It never outlives a confirmation of the deposit
transaction, and it is dropped as soon as any provider returns a status for the transaction. The
first observation time is kept across repeated lookups so the safety interval measures from the
first unanimous not-found answer.

## Messaging

A dead-deposit classification must never describe the trade funds as locked in the deposit: an
unconfirmed deposit locks nothing in the escrow. The wallet inputs funding it remain reserved
until it confirms or is removed, and the messaging must keep the two apart. The messaging must
further state that the verdict comes from external
services, that a transaction dropped from the mempools can still confirm later, that support
options are not available while the trade is in failed trades, and that the trade can be moved
back from there.

## Known limitations

A transaction that was broadcast and later dropped from all mempools is indistinguishable from one
whose input was spent by a conflicting transaction; the dropped one can still confirm. The rules
above therefore only gate a manual, reversible user action and never an automatic transition.

The buyer's broadcast-peer evidence is persisted with the wallet and survives a restart, but an
SPV resync replays the wallet and wipes it. A buyer who resyncs after the transaction vanished
from the network has no broadcast evidence any more and is not offered the verdict; the support
path remains available in that case.
