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
- A wallet transaction counts as the mediated payout only if it passes the checks of
  [payout-seen-in-network.md](payout-seen-in-network.md) for a mediated payout. Then the node
  completes the trade as it does after the message.
- While a refund request or a legacy arbitration is open, a mediated payout counts only once it is
  confirmed, from the wallet as from the peer's message. The delayed payout tx is then published and
  spends the same deposit output, so only a payout that confirmed spent it first, for example one
  that the peer published before the delayed payout tx. While the refund request is open, the node
  keeps watching an unconfirmed payout and completes the trade once it confirms.

## Compatibility

No message and no persisted data change. The watch at startup also covers trades whose mediation
result was accepted with an older version.

## Not covered

The checks of the payout transaction itself, which
[payout-seen-in-network.md](payout-seen-in-network.md) defines. A refund request that the node
learns of only after its wallet check at startup, for example from a mailbox message: until then an
unconfirmed payout counts as in mediation.
