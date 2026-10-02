# DAO state resync warning

## Scope

This specification covers what the desktop application does when the node knows that its DAO state
needs a resync: how the user is warned, and which user actions do not start until the DAO state is
resynced. Checkpoint failures have their own blocking dialog and recovery, see
[`dao-state-checkpoints.md`](dao-state-checkpoints.md).

## Condition

The DAO state needs a resync when initial DAO parsing is complete and at least one of the following
holds:

- the DAO state hash of a seed node conflicts with the hash of the node, see
  [`seed-node-state-hashes.md`](seed-node-state-hashes.md);
- the DAO state hash chain of the node does not connect: a new block is not directly above the last
  block of the chain.

Both marks stay until the node restarts. The resync of the DAO state ends with a restart of the
application. Before initial parsing is complete the node does not know whether its state is in sync,
and this specification does not apply.

## Warning

While the condition holds, the user sees a warning that the DAO state needs a resync, with a button
that opens the DAO network monitor, where the resync starts. The application shows the warning on
its own at most once per block, when the wallet has seen the same block as the DAO state and no
other popup is open or waiting; it does not queue behind another popup. A user who closes it or is
away from the screen sees it again at a later block. In addition, the warning is shown each time the
user starts one of the blocked actions below.

Earlier versions showed the warning again 30 seconds after it was closed, which made the application
hard to use while the actions that depend on the DAO state stayed available.

## Blocked actions

While the condition holds, the following actions do not start. The user gets the warning instead:

- creating or taking an offer, including BSQ swap offers;
- publishing a DAO transaction: a proposal, a blind vote, a bond lockup or unlock, a proof of burn,
  a Burning Man burn, or an asset listing fee;
- sending BSQ or BTC from the BSQ wallet.

These actions depend on the DAO state. A trade uses it for the trade fee in BSQ and for the
receivers of the delayed payout transaction, and the trade protocol does not start a trade while the
DAO state is not in sync. A DAO transaction or a send from the BSQ wallet that is built from a wrong
DAO state can be invalid or burn BSQ. Blocking the action before it starts tells the user why,
instead of a failure later in the protocol. For DAO transactions and sends from the BSQ wallet the
check is repeated right before the transaction is published, because the DAO state can need a resync
while a confirmation or the wallet password window is open.

## Actions that stay available

The warning does not block withdrawing BTC from the trading wallet, any action on open trades or
disputes, deactivating or removing an offer, or the resync itself, so it never keeps a user from
moving BTC or from acting on running trades.

## Not covered

- The API does not use this gate. The checks of the trade protocol still apply to it.
- The automatic vote reveal is not blocked: blocking it would lose the vote of the user.
- Editing an offer and activating a deactivated offer are not blocked. A maker whose DAO state is
  not in sync rejects the takers of the offer.
- This gate blocks nothing while initial DAO parsing is not complete. The trade protocol still does
  not start a trade then.
