# Refund delayed-payout transaction validation

## Purpose

Before a refund agent treats the on-chain transaction evidence for a Bisq v1 refund case as valid,
the alleged delayed payout transaction must prove that it spent the trade's escrow output. Merely
spending another output of the deposit transaction is not evidence that the escrow was spent.

This validation protects refund-agent and DAO reimbursement funds from cases in which traders retain
control of the escrow while presenting an unrelated deposit output as the delayed payout transaction's
input.

## Required transaction binding

The refund-agent transaction-chain validation must establish all of the following:

1. The deposit transaction has inputs funded by both the maker-fee transaction and the taker-fee
   transaction.
2. The delayed payout transaction has exactly one input.
3. That input's previous outpoint is exactly output index `0` of the validated deposit transaction.

Both the deposit transaction ID and the output index are part of the binding. Matching only the
deposit transaction ID is insufficient because a deposit transaction may contain another output,
such as maker change.

Deposit output `0` is the escrow/multisig output defined by the trade protocol. No other deposit
output may be accepted as proof that the delayed payout transaction spent the escrow.

## Receiver validation

When delayed-payout receivers are validated, the receiver schedule and amounts must be derived from
the value of deposit output `0`. Receiver validation is meaningful only after the delayed payout input
has been bound to that same output.

A transaction that spends another deposit output must fail transaction-chain validation even if its
outputs happen to match a receiver schedule calculated from deposit output `0`.

## Failure behavior

A failed outpoint binding must be reported as failed delayed-payout verification. It must not be
represented to the refund agent as successful automatic validation. Any operator override offered by
the application must remain an explicit decision made after the validation failure is displayed.

## Compatibility

This rule does not change transaction serialization or the trade protocol. Valid delayed payout
transactions already spend deposit output `0`; the rule rejects only evidence that did not prove the
escrow output was spent.
