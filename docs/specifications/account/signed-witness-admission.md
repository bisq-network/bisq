# Signed-witness admission and date freshness

## Scope

This specification defines when a `SignedWitness` (and, for the shared date rule, an
`AccountAgeWitness`) may enter the local witness store, and which guarantees the stored dates carry.
It complements [signed-witness-reputation.md](signed-witness-reputation.md), which defines how stored
witnesses are exported as Bisq 2 reputation.

## The date invariant

The date of a `SignedWitness` is not covered by its signature and is not part of its storage
identity. Its absolute value is therefore only trustworthy if it was validated when the record
entered the local store. All later consumers (sign age, trade limits, offer eligibility, the Bisq 2
bridge export) work with the stored date and never check it again against the wall clock.

Consequently, every path that inserts a witness into the local store must validate the date at the
point of entry. A path that skips this validation reopens date forgery for the whole system.

## Admission paths and their rules

### Remotely received P2P payloads

A witness received over the P2P broadcast must have a date within one day (before or after) of the
receiving node's current time.

The bound comparison must be overflow safe. The date is attacker controlled over the full `long`
range; a difference based check (`Math.abs(now - date)`) overflows for extreme values and must not
be used. The whole hostile value range must be rejected in a deterministic way; the difficulty of
hitting a timing window is not an acceptable protection.

This rule applies to `SignedWitness` and `AccountAgeWitness` alike.

### Witness received from the trading peer

The seller signs the buyer's account age witness when the payout transaction gets published and
sends the resulting `SignedWitness` in the `PayoutTxPublishedMessage`. The buyer republishes it, so
that the data is still distributed if the publication of the seller did not reach the network. This
republish uses the local publication path, which does not apply the P2P date check, so the buyer
must validate the peer controlled witness before publishing and storing it.

A freshness check against the current time is not applicable at this point. The message can be
delivered late over the mailbox, so the date of the witness can legitimately be much older than the
current time. A freshness check is only correct for data which is published at the moment it is
created.

The witness is instead required to be exactly the witness the seller was supposed to create for this
trade. The receiver builds that expected witness from its own validated trade data and accepts the
received witness only if it is equal to it:

- the verification method is `TRADE`;
- the account age witness hash is the hash of the receiver's own witness for the payment account
  used in this trade;
- the signer key is the signature key of the trading peer, taken from the contract;
- the owner key is the receiver's own signature key;
- the trade amount is the amount of this trade and is sufficient for signing.

Two fields cannot be derived from the trade data and have their own rules:

- The signature is taken from the received witness and must verify against the account age witness
  hash and the signer key, which are trusted after the comparison above.
- The date is taken from the received witness and must not be before the start of the trade and not
  in the future, with a one day tolerance on both bounds for clock differences between the peers.
  The bounds are compared directly, so an extreme date value cannot overflow. These bounds hold for
  a message delivered late, because the seller signs after the trade started and never in the
  future.

In addition, the signer must have been allowed to sign at the date of the signature. This is
evaluated for the signer key, which is the same subject the recursive chain validation uses and the
same condition the seller checks before signing. It must not be evaluated for the owner key: that
would ask whether the receiver is already a signer, which is a different question and which excludes
the case of a first account being signed.

A rejected witness must not enter the in-memory maps or the persisted append-only store. A rejection
must not fail payout processing; the witness is optional reputation data.

### Locally created witnesses

Witnesses created and signed by the local node (a trader signing at payout, an arbitrator signing)
carry the local current time and need no additional admission check.

## Known limitations

- The date remains outside the deployed `SignedWitness` signature. Anyone can copy an existing
  record and change only its date. Such a copy cannot displace the stored original, because the
  storage identity excludes the date and the first write wins, but the date is not authenticated by
  cryptography. Covering the date by the signature would require a protocol change.
- The date accepted at the trade ingress is the date the peer chose inside the duration of the
  trade, not the date the peer actually signed. A peer can therefore move the date to the start of
  the trade. The gain is bounded by the duration of that single trade.
- The trustworthiness of the stored dates rests on the per ingress discipline described here. Any
  new path which inserts a witness must apply an equivalent validation.
