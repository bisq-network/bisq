# Withdrawal completion

## Scope

This specification defines when the withdrawal of a trade payout to an external address completes
the trade, and how failures before and after that point are reported. It covers the withdrawal
started from the trade API; the equivalent desktop code path exists but is currently not wired to
the user interface.

## Completion invariant

The withdrawal is complete once the withdraw transaction is committed to the wallet. The trade
then leaves open trades, is stored with the closed trades and the requester is notified of the
success, exactly once.

Completion must not wait for the network to accept the transaction. The broadcast future used by
the wallet only completes once connected peers announce the transaction back, which over Tor can
take a long time or never happen; gating the completion on it left trades in open trades although
the funds were sent. A committed transaction is re-broadcast at every start while it is pending,
and it is additionally published via the mempool nodes right away.

## One terminal outcome per request

A withdrawal request has at most one terminal outcome: success once the transaction is committed,
or failure when the commit itself failed, for example for an invalid address or insufficient
funds; an unchecked failure before the commit propagates to the caller as the single outcome. A
failed commit leaves the trade in open trades and can be retried.

A broadcast failure reported after the commit is not a second outcome. It is a notification about
an already completed withdrawal: the trade stays closed and the transaction remains in the wallet
as pending. The desktop shows the notification to the user; the API logs it, since the request has
already been answered.

## Commit boundary

The send must not fail after the wallet has committed the transaction. The steps following the
commit inside the send, such as attaching the broadcast callback, setting the memo and handing the
transaction to the mempool broadcaster, are best-effort; a failure there is logged and must not be
reported to the caller as a failed send, because that would describe a committed withdrawal as not
having happened.

## Recovery

A transaction which the network rejects permanently stays pending in the wallet. The trade is not
reopened; recovery goes through the wallet, for example an SPV resync, which removes the pending
transaction and returns its reserved inputs.
