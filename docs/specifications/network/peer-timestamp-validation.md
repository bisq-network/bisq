# Peer timestamp validation

## Scope

This specification covers protocol timestamps supplied by a remote peer and used as evidence of
current-time freshness.

Timestamps which are not evidence of the current time are governed by their own protocol rule and are
outside this scope. Those rules are subject to the same overflow requirements below. Examples:

- The date of a signed witness received from the trading peer, which is validated against the
  duration of the trade because the message can be delivered late over the mailbox. See
  [`account/signed-witness-admission.md`](../account/signed-witness-admission.md).
- The confirmation time of the Monero transaction used as payment proof, which is validated against
  the start date of the trade. See
  [`trade/xmr-payment-proof-timestamp.md`](../trade/xmr-payment-proof-timestamp.md).

## Security invariant

A peer-controlled timestamp must be rejected unless it falls inside the tolerance assigned to that
protocol field. The comparison must remain correct for every signed 64-bit value.

Validation must compare the timestamp with overflow-safe lower and upper bounds. It must not compute
an absolute difference such as `Math.abs(now - timestamp)`: subtraction can overflow, and the
absolute value of `Long.MIN_VALUE` remains negative, allowing a hostile extreme timestamp to pass a
positive tolerance check.

Bounds validation must fail closed. If the lower and upper bound are derived from independent sources
and the resulting range is empty, no timestamp is accepted. An empty range must not turn a rejection
into an error of the calling code.

## Current protocol tolerances

- The peer date recorded during Bisq v1 deposit-transaction negotiation may differ from local time
  by at most four hours.
- The peer date used during account-age witness verification may differ from local time by at most
  one day.
- The date of an account-age witness or of a signed witness received over the P2P broadcast may
  differ from local time by at most one day. The rule is owned by
  [`account/signed-witness-admission.md`](../account/signed-witness-admission.md).
- A BSQ swap take-offer request may be at most ten minutes before or after local time. The exact
  ten-minute boundary is included.

These tolerances accommodate clock skew and message delay; they do not make the peer clock a trusted
source of time.
