# Fiat buyer payment-account validation

## Scope

This specification defines when a Bisq v1 BTC seller may rely on a fiat BTC buyer's payment account
and continue through deposit publication and settlement. It applies whether the buyer is maker or
taker.

Cryptocurrency trades do not use account-age witnesses and are outside these requirements.

## Offer-level witness data

An account-age witness hash carried by an offer is a preliminary availability hint. It lets a taker
estimate whether a maker may satisfy a trade limit before the maker's private payment-account data is
available.

The hash is not an authorization proof. By itself it does not establish that the witness belongs to
the offer owner, the account committed to the trade contract, or the account that will be used for
payment. A seller must not rely on the offer-carried hash at an irreversible protocol boundary.

## Authoritative buyer validation

For a fiat trade, the buyer must reveal the payment-account payload committed to the trade contract.
Before the seller records that reveal as validated, the seller must verify all of the following:

1. the revealed payload hashes to the buyer payload commitment in the contract;
2. the peer and revealed payment-account data pass the current trade filters;
3. the account-age witness is derived from the revealed payload and the buyer's trade public key;
4. the buyer's signature over the trade challenge is valid; and
5. the witness-derived trade limit at trade time is at least the negotiated trade amount.

Successful validation is persisted. Merely receiving or persisting a payment-account payload is not
equivalent to successful validation because a later filter or witness check may fail.

## Deposit publication

Before the seller broadcasts the fiat deposit transaction, two independent prerequisites must have
completed:

- the buyer payment account has passed the authoritative validation above; and
- the buyer has received the deposit and delayed-payout transaction message, so the buyer retains the
  recovery transaction before funds are locked.

The prerequisites may complete in either order. Completing either one must re-evaluate deposit
publication, but publication must remain blocked until both are persisted as complete. Repeated
messages and retries must not cause a second logical publication.

The seller must persist the finalized deposit transaction before publication can be deferred. If the
seller restarts while either prerequisite is still pending, it must restore the exact transaction,
resume delivery of the deposit and delayed-payout transaction message when necessary, and
idempotently re-evaluate publication. A stored buyer-account reveal must be processed through the
same validation path as a directly delivered reveal. Restart recovery must not depend on the buyer
resending a reveal that the seller already received.

The buyer receives a fully signed deposit transaction as part of this exchange and can broadcast it
with a modified client. The seller therefore must also enforce the validation state at settlement;
delayed seller publication alone is defense in depth, not the complete authorization boundary.

## Settlement gates

For a fiat trade, the seller must fail closed if buyer account validation is absent before either of
these transitions:

- accepting the buyer's payment-started message; or
- signing and broadcasting the normal payout transaction after the seller confirms receipt.

For a trade persisted by an older release without an explicit validation result, the seller may
reconstruct the result from the persisted revealed payload and the original trade-time witness
evidence. Reconstruction must perform the same payload-to-witness binding, signature, and trade-limit
checks. The absence of the newly introduced marker alone must not block an in-progress trade after an
upgrade. A missing reveal or invalid evidence remains a hard failure.

Current filters are still applied independently at the later protocol boundary. This allows an
account or peer banned after the initial reveal to be rejected without changing the historical
account-age calculation.

## Security rationale

Without these gates, a malicious BTC buyer can omit the reveal message and bypass the seller's real
account-age and banned-account checks. A buyer who is also an offer maker can additionally place a
borrowed public witness hash in an offer so the preliminary offer filter appears to pass. Mandatory
validation of the revealed, contract-bound account closes both paths.
