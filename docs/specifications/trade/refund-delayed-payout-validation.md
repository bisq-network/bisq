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

The deposit transaction named by the dispute is bound to the dispute and contract at admission. The
serialized deposit transaction carried by the dispute must hash to the dispute's deposit transaction
ID, and its escrow output must match the contract-bound script and value described below. The
transactions used for the close-time validation are fetched independently by the IDs carried in the
dispute and its contract, and the fetched deposit must satisfy the same checks.

## Contract-bound escrow

Deposit output `0` must use the P2WSH script derived from the contract's buyer and seller 2-of-2
multisig public keys. Its value must satisfy the protocol equation exactly:

```text
deposit output 0 = trade amount
                 + buyer security deposit
                 + seller security deposit
                 + trade transaction fee
```

The trade amount and deposits come from the hashed contract JSON. The multisig keys come from the
serialized contract and must derive the script actually funded on chain; legacy contract JSON does
not commit those keys, so this script check is an escrow-consistency rule rather than independent
proof that two traders accepted the contract. All amounts and the derived trade transaction fee must
be positive. The fee used to reconstruct the delayed-payout receiver schedule is derived from the
validated output value and contract pot; the dispute-carried fee must equal that value and is not an
independent authority.

These checks apply both to the serialized deposit accepted with a refund dispute and to the fetched
deposit used immediately before a refund is authorized. A correctly shaped DPT spending an
underfunded or differently scripted output is not valid refund evidence.

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

The receiver schedule and amounts must be derived from the value of deposit output `0` and the trade
transaction fee derived by the contract-bound escrow check. The outputs of the delayed payout
transaction must match that schedule exactly in number, address and value.

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

## Refund authorization bound

After all delayed-payout outputs have been validated, the maximum normal refund authorization is:

```text
minimum(contract trade amount + both security deposits,
        validated delayed-payout receiver output sum)
```

Buyer and seller payout amounts must each be non-negative and their sum must not exceed that maximum.
The validation result binds the contract hash, deposit transaction ID, delayed-payout transaction ID,
validated chain values, trade fee, receiver-selection height, legacy donation address and exact
buyer/seller allocation. The same binding must still match when the payout confirmation is accepted,
immediately before a refund-wallet payout and immediately before the dispute result is signed.

Closing the second trader's dispute row must perform the validation again; a peer row's closed flag
is not evidence that the same contract, transactions and payout allocation were validated.

## Failure behavior

A failed transaction binding or output validation must be reported as failed delayed-payout
verification. It must not be represented to the refund agent as successful automatic validation. Any
operator override offered by the application must remain an explicit decision made after the
validation failure is displayed.

## Compatibility

These rules do not change transaction serialization or the trade protocol. Valid deposits already
use the contract multisig script and protocol value equation, and valid delayed payout transactions
already spend deposit output `0` and pay the protocol receivers. The rules reject only inconsistent
evidence or a refund exceeding the value proved by that evidence.

## Known limitations

The following inputs of the validation are currently taken from the dispute or its contract as
supplied by the dispute opener and are not recomputed or bounded locally. They do not allow the
opener to redirect the escrow to an address of their choice, because the output rules above bind
every output to DAO-derived addresses, but they are not authenticated facts about the trade:

- The Burning Man selection height is accepted as carried in the dispute. It is not bound to the
  block height of the deposit transaction, so any DAO snapshot may be selected for the receiver
  schedule.
- The transactions returned by the block explorer are trusted: they are not compared with the
  requested transaction IDs, no confirmation depth is required, and the sequence number and lock
  time of the delayed payout transaction are not checked.
- When the transactions cannot be fetched at all, the application warns and lets the agent
  continue, without any of the validation above having run.

Closing these gaps requires binding the deposit transaction and the receiver inputs to the contract
and to local data, and a payout policy based on verified chain value. That work is tracked as a
separate security fix.
