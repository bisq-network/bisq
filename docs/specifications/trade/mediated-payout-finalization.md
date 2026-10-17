# Mediated payout finalization

## Scope

This specification defines when a trader's node publishes the mediated payout after the mediator
proposed a result, and what the trader can still do after accepting. It applies while the
mediation result is proposed (dispute state MEDIATION_CLOSED) and the payout is not published.

## Rules

- Accepting the mediation result signs the mediated payout and sends the signature to the peer.
  The signature is the acceptance: the peer can publish the payout with it, so it cannot be
  withdrawn. The desktop therefore disables rejecting the result once the trader has signed.
- A node that has signed the mediated payout and has the peer's signature publishes it, whichever
  of the two came last. The accept button publishes it when the peer's signature is already there,
  and the processing of the peer's signature publishes it when the trader has already accepted,
  also for a signature that was kept until the mediation result was applied
  ([`mediated-payout-peer-signature.md`](mediated-payout-peer-signature.md)). A node that is in that
  state at startup publishes it then.
- Without a click, the node publishes it only once its wallet is synced and has enough peers for the
  broadcast, the wallet state in which the dispute messages, such as a refund request of the peer,
  are applied. At startup it also waits until the mailbox messages that it finds in the P2P network
  are decrypted and handled, which takes a while after a long time offline. While the wallet is not
  ready, the node checks again at each new block. It does not publish the payout if its wallet knows
  the delayed payout tx: the wallet gets that tx only once a trader published it to request
  arbitration, and it spends the deposit output that the payout would spend.
- If the publication without a click fails, the node logs it, and it is no error of the trade, as
  the trader did not start it: the UI shows a trade error at once and offers to move the trade to
  the failed trades. Unless the failed attempt had put the payout tx into the wallet for the
  broadcast, the node tries again at the next start. Until the attempt has finalized the payout tx,
  the trader can also use the accept button, which shows a failure of its own attempt, and a
  signature of the peer that arrives starts another attempt. A payout tx in the wallet is announced
  again by the wallet, and the next start closes the trade as mediated.
- After publishing, the node sends the payout to the peer and closes the disputed trade, as after
  publishing it with the accept button.
- Both nodes can publish the mediated payout at the same time. It is the same transaction on both
  sides, so this does no harm. A node therefore also accepts the peer's
  MediatedPayoutTxPublishedMessage after its own payout was published, and keeps its own payout.
  If the trade was closed without a payout of the node, for example by the refund agent, or while
  its wallet does not know its payout yet, for example after a resync, the node rejects the message.

Without the rule for a signature that arrives after the own acceptance, both traders could accept
before the other's signature arrived. Then both nodes have both signatures, both show that they
wait for the peer, and the payout is published only when a trader clicks accept again.

Without the wait for the wallet and the check of the delayed payout tx, a node that starts after the
peer went to arbitration publishes a payout that conflicts with the delayed payout tx and never
confirms, and closes the trade as settled by the mediation.

## Compatibility

No message and no persisted data change.

A node on an older version does not publish when the peer's signature arrives after its own
acceptance. If our node has the older peer's signature, our node publishes, and the older peer
completes the trade with our MediatedPayoutTxPublishedMessage as before. If the older peer publishes
with its accept button at the same moment, it rejects our message, which only logs an error there.

## Not covered

Detecting a payout that the peer published while the node, which has only its own signature, was
offline. A delayed payout tx that the peer publishes at the same time, or whose
PeerPublishedDelayedPayoutTxMessage has not arrived, as that tx pays nothing to the wallet and the
wallet does not watch the deposit output. A refund request that reaches the node only after the
check, for example in a later mailbox message. Mailbox messages that arrive only after the startup,
for example when the node starts without reaching a seed node. Whether the seller can still confirm
the payment receipt after accepting:
[repeated-payout-confirmation.md](repeated-payout-confirmation.md). A
MediatedPayoutTxPublishedMessage that arrives while a refund request is open is processed as before.
