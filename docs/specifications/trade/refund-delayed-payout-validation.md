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

## Contract signature consistency and trust limits

Trader contract signatures are optional at dispute admission, including for refunds. Each signature
that is present must verify against the respective trader's signature key supplied in the contract
over the supplied contract JSON. A missing signature does not invalidate the dispute; a present
but non-verifying signature does.

Production disputes copy the trade's signature fields, which normally hold only the local party's
signature. The taker retains the received maker signature separately in its peer state. Since 1.7,
each party also re-signs the payment-account-enriched contract JSON locally without exchanging that
final signature. Requiring both final signatures would therefore reject legitimate refund disputes.

These checks establish consistency with the supplied keys, not independently authenticated peer
acceptance. Trader key rings, including the offer's key ring, are excluded from contract JSON and
come from the dispute opener. An opener can replace the peer payout address and peer signing key,
regenerate JSON/hash, and supply signatures from both controlled keys. Deposit validation anchors the
separate multisig keys, not the trader signature keys or payout addresses. Neither a consistent
contract hash nor two verifying signatures closes this payout-address substitution vulnerability.

Independent proof of peer acceptance would require trusted evidence binding the signing keys and
exact payout contract to the escrow, or a prior independently trusted commitment. The current
signature checks do not provide that evidence. This compatibility rule preserves admission of
existing trades; it does not establish that the payout-address vulnerability has been resolved.

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

1. Each raw transaction returned by the explorer hashes to the exact maker-fee, taker-fee, deposit or
   delayed-payout transaction ID for which it was requested. The parsed transactions must also match
   the IDs in the contract and dispute before their relationships are checked.
2. The deposit transaction has at least two inputs, and its inputs are funded by both the maker-fee
   transaction and the taker-fee transaction named in the contract.
3. The delayed payout transaction has exactly one input.
4. That input's previous outpoint is exactly output index `0` of the validated deposit transaction.
5. The delayed payout transaction passes normal transaction verification, has the contract locktime,
   and its input sequence is exactly `0xfffffffe`, which activates that absolute locktime without
   opting in to RBF.

Both the deposit transaction ID and the output index are part of the binding. Matching only the
deposit transaction ID is insufficient because a deposit transaction may contain another output,
such as maker change.

Deposit output `0` is the escrow/multisig output defined by the trade protocol. No other deposit
output may be accepted as proof that the delayed payout transaction spent the escrow.

## Confirmation and finality

The explorer status response for both the deposit and delayed payout transaction must contain the
same transaction ID that was requested and report a confirmed block height. That block height must
not be ahead of the locally parsed DAO chain height, and each transaction must have at least one
confirmation relative to that local height. A confirmed deposit implies that its maker-fee and
taker-fee ancestors are also confirmed.

The contract-derived trade start height is:

```text
contract locktime - protocol locktime delay for the contract payment method
```

The confirmed deposit height must be at or after that start height and no later than the contract
locktime. The delayed payout must confirm at or after the deposit and strictly after the contract
locktime. A transaction merely known to the explorer's mempool is not confirmed evidence, and a DPT
whose locktime was already mature before its deposit confirmed is not eligible for automatic refund
authorization.

The explorer remains the source of the transaction inclusion record; these rules bind its response
to cryptographically identified transactions and a locally known chain height. They do not introduce
a new Bitcoin merkle-proof protocol.

## Burning Man selection height

For a modern Burning Man trade, the expected receiver snapshot is calculated locally from the
contract-derived trade start height. The dispute-carried selection height must equal that snapshot
or one immediately adjacent snapshot grid, preserving the protocol's ten-block peer-height
tolerance. It must also be no later than the snapshot available at the confirmed deposit block.

The legacy selection value `0` is accepted only when the derived trade start predates the mainnet
minimum Burning Man snapshot height (`767950`). A modern-height dispute cannot select the legacy
output rule, and a dispute cannot choose an arbitrary historical DAO snapshot.

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
        deposit output 0 value - verified trade transaction fee)
```

For a deposit that satisfies the contract-bound escrow equation both terms are equal, so the bound
is the contract pot. This is the same limit that the close dialog offers before the delayed payout
transaction has been fetched (see [`../dispute/refund-direct-payout.md`](../dispute/refund-direct-payout.md));
the two limits must not diverge, otherwise a full-pot refund is accepted in the form and rejected at
close. The delayed-payout output sum is deliberately not the bound: it is smaller than the escrow by
the delayed-payout miner fee, which the refund has always covered, and it is only known after the
transaction fetch. The output sum is still recorded in the validation binding.

Buyer and seller payout amounts must each be non-negative and their sum must not exceed that maximum.
The validation result binds the contract hash, deposit transaction ID, delayed-payout transaction ID,
validated chain values, trade fee, receiver-selection height, legacy donation address and exact
buyer/seller allocation. The same binding must still match when the payout confirmation is accepted,
immediately before a refund-wallet payout and immediately before the dispute result is signed.

Closing the second trader's dispute row must perform the validation again; a peer row's closed flag
is not evidence that the same contract, transactions and payout allocation were validated.

## Failure behavior

A failed transaction fetch, identity check, confirmation/finality check, contract binding, output
validation or payout-bound check must be reported as failed delayed-payout verification and terminate
the normal close attempt. The application must not offer an action that continues from failed or
unavailable evidence into a refund-wallet payout or a signed refund result.

The agent may keep the ticket open, add notes, retry after temporary provider failure, or investigate
the transactions separately. Manual investigation is not authorization to emit the same payout or
signed artifact produced by successful automatic validation.

## Development networks

Regtest has no block explorer, so the transaction evidence described above cannot be fetched there.
On regtest the refund close flow skips the evidence validation and the payout and result gates that
depend on it, so developers can exercise the close flow in a local network. The skip is selected by
the regtest network only; it must never apply to mainnet, and it does not weaken the amount limits,
the receipt consumption or the intake validation, which use locally available data. Testnet is not
supported: the evidence fetch fails there and the close attempt terminates as on mainnet.

## Compatibility

These rules do not change transaction serialization or the trade protocol. Valid deposits already
use the contract multisig script and protocol value equation, and valid delayed payout transactions
already spend deposit output `0` and pay the protocol receivers. The rules reject inconsistent
evidence, a refund exceeding the proved value, and the unusual timing cases described below.

Automatic validation also rejects a deposit that confirmed only after its DPT locktime matured and
a legacy-selection trade derived to start at or after the Burning Man snapshot activation boundary.
Those unusual historical cases require investigation rather than allowing opener-carried timing or
the legacy selector to weaken the normal authorization rules.
