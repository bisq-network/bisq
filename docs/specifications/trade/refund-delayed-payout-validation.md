# Refund delayed-payout transaction validation

## Purpose

Before a refund agent treats the on-chain transaction evidence for a Bisq v1 refund case as valid,
the alleged delayed payout transaction must prove that it spent output `0` of the deposit
transaction named by the dispute and paid that value to the receivers defined by the trade
protocol. Merely spending another output of the deposit transaction is not evidence that the escrow
was spent, and spending the escrow output to an arbitrary destination is not evidence that it was
burned.

This validation protects refund-agent and DAO reimbursement funds from cases in which traders retain
control of the escrow while presenting a transaction that does not burn it as the delayed payout
transaction.

The deposit transaction named by the dispute is bound to the dispute at admission: the serialized
deposit transaction carried by the dispute must hash to the dispute's deposit transaction ID. The
transactions used for the validation below are fetched from a block explorer by the IDs carried in
the dispute and its contract.

## Required transaction binding

The refund-agent transaction-chain validation must establish all of the following:

1. The deposit transaction has at least two inputs, and its inputs are funded by both the maker-fee
   transaction and the taker-fee transaction named in the contract.
2. The delayed payout transaction has exactly one input.
3. That input's previous outpoint is exactly output index `0` of the validated deposit transaction.

Both the deposit transaction ID and the output index are part of the binding. Matching only the
deposit transaction ID is insufficient because a deposit transaction may contain another output,
such as maker change.

Deposit output `0` is the escrow/multisig output defined by the trade protocol. No other deposit
output may be accepted as proof that the delayed payout transaction spent the escrow.

## Output validation

Spending the escrow output is necessary but not sufficient. Two traders who hold the escrow keys
can spend the escrow output to any destination, so the refund-agent validation must also establish
that its value went to the receivers defined by the trade protocol. Output validation is meaningful
only after the delayed payout input has been bound to deposit output `0`.

Which output rule applies is selected by the dispute-carried Burning Man selection height. Because
the dispute opener supplies that value, both rules must be equally strict; the opener must not be
able to select a weaker rule.

### Burning Man receivers (selection height greater than zero)

The receiver schedule and amounts must be derived from the value of deposit output `0`. The outputs
of the delayed payout transaction must match that schedule exactly in number, address and value.

A transaction that spends another deposit output must fail transaction-chain validation even if its
outputs happen to match a receiver schedule calculated from deposit output `0`.

### Legacy donation address (selection height zero)

Trades created before the Burning Man receivers existed paid the whole escrow, minus the miner fee,
to a single DAO donation address. For such a dispute the delayed payout transaction must:

1. have exactly one output;
2. pay that output to the donation address recorded in the dispute; and
3. that address must be a current or past value of the DAO donation-address parameter.

Comparing only the dispute-carried address string with the DAO parameter history is insufficient,
because it does not prove where the fetched transaction actually paid. A transaction that spends the
escrow output to any other destination must fail validation even when the dispute claims the legacy
rule.

## Failure behavior

A failed transaction binding or output validation must be reported as failed delayed-payout
verification. It must not be represented to the refund agent as successful automatic validation. Any
operator override offered by the application must remain an explicit decision made after the
validation failure is displayed.

## Compatibility

These rules do not change transaction serialization or the trade protocol. Valid delayed payout
transactions already spend deposit output `0` and pay the protocol receivers; the rules reject only
evidence that did not prove the escrow output was spent and burned.

## Known limitations

The following inputs of the validation are currently taken from the dispute or its contract as
supplied by the dispute opener and are not recomputed or bounded locally. They do not allow the
opener to redirect the escrow to an address of their choice, because the output rules above bind
every output to DAO-derived addresses, but they are not authenticated facts about the trade:

- Deposit output `0` is not bound to the contract: neither its script is compared with the 2-of-2
  multisig of the contract's trader keys nor its value with the contract's trade amount, security
  deposits and trade fee. A self-consistent dispute with a small deposit output can therefore pass
  while the contract declares a larger trade.
- The Burning Man selection height is accepted as carried in the dispute. It is not bound to the
  block height of the deposit transaction, so any DAO snapshot may be selected for the receiver
  schedule.
- The trade fee is accepted as carried in the dispute. It sets the fee rate of the receiver schedule
  and therefore how much of the escrow becomes miner fee and which small receivers are filtered out.
  The trade protocol binds this fee to the escrow value (deposit output `0` equals trade amount plus
  both security deposits plus the trade fee); the refund-agent validation does not apply that rule
  yet.
- The transactions returned by the block explorer are trusted: they are not compared with the
  requested transaction IDs, no confirmation depth is required, and the sequence number and lock
  time of the delayed payout transaction are not checked.
- When the transactions cannot be fetched at all, the application warns and lets the agent
  continue, without any of the validation above having run.

Closing these gaps requires binding the deposit transaction and the receiver inputs to the contract
and to local data, and a payout policy based on verified chain value. That work is tracked as a
separate security fix.
