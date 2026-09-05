# Refund-agent direct payout authorization

## Scope

This specification defines the authorization and amount invariants for a refund agent creating a direct BTC payout
from the refund wallet. It applies independently of how many dispute rows, peer tickets, reopened tickets, or restored
records refer to the same trade evidence.

Mediated payouts from the trade multisig and delayed-payout transactions to Burning Men are outside this scope.

## One-time receipt consumption

A verified deposit transaction and its spending delayed-payout transaction form the funding evidence for a direct
refund. Their transaction IDs must be parsed and represented canonically before they are compared or recorded.

Each funding chain authorizes at most one direct refund-wallet payout. A prior payout conflicts with a proposed payout
when either the canonical deposit transaction ID or the canonical delayed-payout transaction ID is the same. Matching
only by dispute trade ID, trader ID, or active-ticket count is insufficient. In particular, the two legitimate peer
dispute rows for one trade do not authorize two wallet payouts.

Receipt-consumption checks must include closed and retained dispute records, not only the selected UI row. A local
wallet transaction that carries the canonical refund-receipt memo is also evidence that the receipt was consumed. The
wallet memo is recovery evidence; it is not transmitted on chain and does not replace transaction and dispute
validation.

A malformed funding transaction ID in a retained record is ignored individually, with a logged warning. If that
record carries a payout transaction ID, its other parseable funding transaction ID remains receipt-consumption
evidence and must be compared under the conflict rule above. A retained record with no parseable funding transaction
ID cannot match a receipt and must not prevent payouts for unrelated receipts. The ticket selected for payout must
itself carry both parseable IDs; otherwise no payout is authorized for it.

## Durable reservation and publication

Before a direct payout is committed to the wallet or broadcast, its transaction ID must be bound to the receipt and
the reservation must be durably persisted. Only one refund payout reservation may be awaiting persistence at a time;
otherwise a later reservation's serialized snapshot could carry the temporary marks of an earlier reservation whose
write subsequently fails. Every locally stored refund row presenting the same funding chain, that is the same deposit
and the same delayed-payout transaction, is marked with that payout transaction ID. A row that shares only one of the
two transactions is not marked: it may belong to a different trade, and marking it would let a crafted record transfer
the paid state to an unrelated ticket. Such a row remains blocked by the conflict rule above for as long as the paid
record exists. For the same reason, a conflict found when a ticket is closed is reported to the operator but does not
mark the conflicting record. Restoring a dispute with a payout transaction ID must also restore its already-paid state.

If durable reservation fails, the transaction must not be broadcast. Because nothing has been committed or broadcast
at that point, the reservation marks are removed again and the receipt remains payable; a failed reservation must not
leave a persisted paid state without a transaction. Once reservation succeeds, timeout, ambiguous
broadcast status, restart, reopening, or a later close attempt must not create a replacement payout. These cases fail
closed because the original transaction may already have propagated. Recovery may rebroadcast or inspect the original
transaction, but it must not silently clear receipt consumption.

Durable persistence is asynchronous and is not itself continuing payout authorization. After it succeeds and
immediately before wallet commit, the original dispute row, current claim eligibility, manual approval, evidence and
exact payout allocation must still match. Expired eligibility or changed dialog state stops publication and result
signing. The successful durable reservation remains consumed and requires investigation; it must not be silently
cleared to enable a replacement payout. The operator must be told, at the failed attempt itself, that no transaction
was committed or broadcast and which recorded transaction ID now blocks a replacement payout. A later close attempt
that finds a consumed receipt whose transaction is unknown to the wallet must say so as well, instead of reporting
that a payout was created or paid. Absence from the current wallet does not establish whether the recorded transaction
was broadcast or paid; the message must report that uncertainty and require investigation before closing the ticket.

This ordering deliberately favors preventing a second spend over automatic recovery. A process failure after durable
reservation but before wallet commit or broadcast can require manual investigation of the recorded transaction ID.

## Payout amount

Buyer and seller payout amounts must each be non-negative. At least one output must be positive when a payout
transaction is created. Because both amounts are non-negative, their sum equals the value of the actual trader
outputs; a negative amount must never offset a larger positive output. Closing a ticket with zero payout to both
traders creates no transaction; it consumes no receipt. It still requires validated transaction evidence and an
authenticated claimant (or the temporary legacy exception) before the refund result is signed, so a ticket whose
funding transaction identifiers are malformed cannot be closed through the application even with a zero payout.

When exactly one trader receives a positive payout, that role must have authenticated the claim with its escrow key.
A payout to both traders is exceptional and requires the operator to verify both addresses directly. A temporary
manual-verification exception for stored legacy disputes expires on 1 November 2026. These rules are
defined in [`refund-claimant-authentication.md`](refund-claimant-authentication.md).

The trader-output sum must not exceed either:

- the payout pot represented by the contract's trade amount and security deposits; or
- the capacity evidenced by the deposit multisig output after the recorded trade-transaction fee reserve, when that
  reserve is available.

The effective limit is the lower value. The wallet transaction-construction boundary must enforce the output sum
against the supplied effective limit even when the UI already performed the same validation. Mining fees are separate
wallet inputs and must also be non-negative.

## Inbound dispute state

An inbound `OpenNewDisputeMessage` describes a newly opened dispute. Its dispute payload must be in `NEW` state and
must not contain either a `DisputeResult` or a dispute payout transaction ID. Results and direct-payout state are local
agent decisions created after intake; accepting them from the opener would allow an untrusted message to seed payout
amounts or paid-state metadata.

Locally persisted drafts are not inbound new-dispute messages and may retain a locally authored result according to
the existing dispute workflow.

The deposit transaction ID and, when present, the delayed-payout transaction ID of an inbound dispute must be
canonical 32-byte transaction IDs. Replay detection and receipt consumption key on these IDs, so a dispute carrying
any other value is rejected at intake instead of being stored.

Trader contract signatures are optional at admission, but any supplied signature must verify. Verification with
dispute-supplied keys does not independently authenticate the contract payout addresses; see the trust limits in
[`../trade/refund-delayed-payout-validation.md`](../trade/refund-delayed-payout-validation.md).
Admission must additionally authenticate the opener with the role-specific escrow key as defined in
[`refund-claimant-authentication.md`](refund-claimant-authentication.md).

## Compatibility and historical records

The persisted dispute payout transaction ID is the compatibility-safe receipt marker. Payouts created by older
releases that stored neither this marker nor the wallet receipt memo cannot always be identified automatically.
Operators must correlate historical payout transactions before paying an old or restored refund ticket whose receipt
may already have been used. The independently specified claimant proof adds a serialized dispute field and is required
for new refund authorization after upgrade, subject to its temporary grace period for legacy records.
