# Detection of the mediated payout

## Scope

This specification defines how a trader's node that has accepted a mediation result learns that the
peer published the mediated payout.

## Rules

- A trader who has accepted the mediation result has signed the mediated payout. Once the peer has
  that signature, the peer can publish the payout.
- The node learns of the payout from the peer's MediatedPayoutTxPublishedMessage, and independently
  from its wallet: it watches its payout address of the trade for the mediated payout. The message
  can be lost, so the wallet is the fallback.
- The node watches from its acceptance until the payout is known, also across restarts. At startup
  it watches again for a trade in phase DEPOSIT_CONFIRMED, FIAT_SENT or FIAT_RECEIVED for which it
  has signed the mediated payout.
- A wallet transaction counts as the mediated payout only if it spends the deposit and its outputs
  pay the amounts of the mediation result to the traders' payout addresses. Then the node completes
  the trade as it does after the message.

## Compatibility

No message and no persisted data change. The watch at startup also covers trades whose mediation
result was accepted with an older version.

## Not covered

The checks of the payout transaction itself. In particular, a payout that the wallet first sees in
a block has no witness data, and whether it is accepted depends on those checks.
