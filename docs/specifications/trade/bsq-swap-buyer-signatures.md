# BSQ swap buyer signatures

## Scope

This specification defines what the BTC seller of a BSQ swap must verify about the buyer's
signatures before it accepts the finalized swap transaction, and the rule against buyer inputs
listed twice, which the seller also applies before it signs. It applies whether the seller is maker
or taker.

The seller builds the swap transaction from the buyer's input descriptions, signs its own inputs and
sends the transaction to the buyer. The buyer signs its inputs, publishes the transaction and sends
it back. The seller compares the returned transaction with its own after it removes the scripts and
witnesses of the buyer's inputs, so this comparison fixes everything except the buyer's signatures.
The other checks of the buyer's input descriptions before the seller signs, against its DAO state,
are outside this scope.

## Rule

Before the seller applies the returned transaction to the trade, marks the trade as completed,
closes its offer as maker or publishes the transaction, each buyer input must be in the form in
which a Bisq wallet signs it, and its signature must be valid for the value and the script of the
spent output. The form is the one defined for seller inputs in
[`bsq-swap-seller-inputs.md`](bsq-swap-seller-inputs.md#seller-signatures).

Without this rule a buyer can return the transaction with invalid signatures. The seller then
commits its BTC inputs to a transaction which the network rejects and, as maker, closes its offer,
while the buyer keeps its BSQ and can repeat this against other offers.

If the seller already holds the transaction, for example because its wallet received it from the
network, it does not process the returned transaction again.

## Buyer inputs listed twice

The buyer's input descriptions must name different outputs. The seller rejects a request which
names the same output twice before it signs, and it rejects a returned transaction which spends the
same outpoint twice.

An output listed twice counts twice when the seller checks the value of the buyer's inputs against
its DAO state, and the seller would build a transaction which spends the same outpoint twice. The
network rejects such a transaction, but the buyer can sign both copies, so the rule above accepts
it. The seller would then complete the trade and, as maker, close its offer on a transaction which
never confirms.

## Compatibility

The buyer's BSQ wallet signs its inputs in this form and lists each output once, so the rules do not
reject an honest buyer. Messages and persisted data do not change.

## Not covered

- The buyer holds the transaction signed by the seller from the time the seller sends it, and can
  publish it later. This option is part of the protocol design and does not depend on this rule.
- The seller marks the trade as completed and, as maker, closes its offer before the broadcast
  result is known, and a broadcast timeout counts as success.
