# Payout transaction seen in the network

## Scope

A trader usually learns about the published payout transaction from the message of the peer that
published it. Until then, the trader's node watches the payout address of the trade in its wallet.
When a transaction to that address appears, the node checks it, and if it is the payout of the
trade, the node completes the trade with it. The buyer's node does this after it confirmed that it
started the payment, and either trader's node does it after accepting a mediation result.

## Rules

- The transaction must spend output 0 of the deposit transaction, the 2-of-2 multisig output of both
  traders, and pay exactly the expected amounts to the payout addresses of the contract. For a
  mediated payout, the expected amounts are those of the accepted mediation result.
- While the transaction is unconfirmed, its witness must contain valid signatures of both traders
  for that spend, in the form in which a BISQ wallet signs it, with SIGHASH_ALL.
- Once the transaction is in a block, its signatures are not checked.

An unconfirmed transaction is only what a peer relayed. Without the signature check, a peer could
relay a transaction that can never be mined and make the node treat the trade as paid out. A
transaction in a block has passed the consensus checks, which include its witness, and its
transaction id, which the block commits to, covers the input and the outputs that the rules check.

A light wallet receives the transactions of a block without their witness data, because Bitcoin
Core sends the transactions of a filtered block without witness. So a payout that the wallet first
sees in a block has no witness to check, for example when the node was offline while the payout was
published, or after a resync of the wallet.

## Compatibility

No message and no persisted data change.

Before, the node ignored a payout transaction without witness data. A trade whose payout message
did not arrive, and whose payout the wallet first saw in a block, was never completed, although the
payout was in the wallet. At each start, the buyer's node watches the payout address again for a
payout without mediation, so such a trade now completes at the next start.

## Not covered

The payout transaction received in the peer's message is checked as before, including its
signatures. A mediated payout after a restart: the node watches the payout address for it only from
accepting the mediation result until it stops. The delayed payout transaction and refunds.
