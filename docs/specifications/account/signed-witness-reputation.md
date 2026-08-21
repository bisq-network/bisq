# Signed-witness reputation export

## Scope

This specification defines when a Bisq 1 signed account-age witness may be exported as Bisq 2
signed-witness reputation. It extends the ownership rules in
[account-age-reputation.md](account-age-reputation.md); owning an account-age witness is necessary but
is not sufficient for signed-witness reputation.

## Ownership proof

The account owner must submit the same witness hash preimage used by the account-age protocol:

```text
accountInputDataWithSalt || ownerPublicKey
```

The bridge recomputes the 20-byte account-age witness hash and verifies a DSA signature made by
`ownerPublicKey`. The signed message is canonical and length-prefixed and includes:

- domain `BISQ2_SIGNED_WITNESS_REPUTATION_V2`;
- protocol version `2`;
- target Bisq 2 profile id;
- witness hash;
- salted account-input bytes; and
- owner public key.

The distinct domain prevents an account-age proof from being replayed as a signed-witness proof.
The bridge must resolve the recomputed hash to a stored Bisq 1 account-age witness.

## Qualifying signed-witness chain

The bridge returns a sign date only when at least one `SignedWitness` leaf for that account-age
witness satisfies every existing Bisq 1 trust-chain rule. In particular:

- the leaf signature must be valid;
- a trade-signed leaf's `witnessOwnerPubKey` must equal the public key from the ownership proof;
- a directly arbitrator-signed leaf may carry a different historical owner key, because versions
  before commit `09141eba92` accidentally exchanged the buyer and seller owner keys when producing
  those records;
- a trade-signed leaf must lead through the required signer-age intervals to a valid arbitrator
  root;
- circular, overlong, too-young and banned chains do not qualify; and
- the authoritative date is the earliest date among leaves which individually satisfy all rules.

A signature-valid record from a chain which does not reach a trusted root must not supply the date.
The same individually qualified set supplies signed-witness dates to Bisq 1 status and account-age
consumers; an invalid leaf must not make a witness appear older in those paths either.
The public `witnessOwnerPubKey` field alone is not an ownership proof because it is not committed by
the deployed `SignedWitness` signature. Trade-signed leaves remain bound to the independently proven
owner key. For a directly arbitrator-signed leaf, the arbitrator signature authenticates the account-age
witness hash and the ownership proof independently binds the requester key to that same hash. The
historically incorrect auxiliary owner field is therefore not used for that compatibility case. The
proven owner key must still pass the deny-list policy.

Arbitrator roots must have a valid signature and a key accepted by the active environment. Developers
who run with `useDevPrivilegeKeys=true` may explicitly enable `allowMainnetSignedWitnessesWithDevPrivilegeKeys` to add the
immutable legacy mainnet arbitrator keys to the signed-witness trust roots. The option is disabled by
default and affects only signed-witness validation; it must not authorize those legacy keys for live
dispute-agent registration or other privileged operations.

## Bridge contract and failures

Protocol version `2` sends the profile id, witness hash, salted account input, owner key and ownership
signature to `VerifySignedWitnessOwnership`. The response contains the shared witness nullifier and
the fixed one-day UTC bucket containing the authoritative qualifying sign date. Nullifier
construction, date bucketing and conservative Bisq 2 scoring are defined by the account-age
reputation specification. The requester does not supply either the account-age date or sign date.

The legacy date-only RPC must reject authorization use. An unsupported version, invalid ownership
proof, missing witness, trade-leaf owner mismatch, or missing qualifying chain fails closed and
returns no date.

## Bisq 2 requirements

Bisq 2 must additionally authenticate the confidential sender as the target profile, persist the
accepted proof before publication, include the nullifier and date bucket in oracle-authorized data,
and apply the shared one-witness/one-profile rule across both account-age and signed-witness
reputation sources. It must not publish the raw Bisq 1 hash or exact sign date. Legacy
signed-witness authorized data lacks the witness identity and contributes no score.

## Auditability of legacy authorization

The 2026-08-15 resource audit parsed 27 311 unique signed-witness records and verified their structural
owner-key and account-hash fields. These records describe Bisq 1 trust-chain inputs only. They contain
no Bisq 2 profile id, requester key, oracle request or authorization result, so they cannot reveal
whether the legacy missing-ownership-binding vulnerability was exploited. The evidence requirements
and the prohibition on drawing a no-exploitation conclusion from Bisq 1 resources are specified in
[account-age-reputation.md](account-age-reputation.md#auditability-of-legacy-authorization).
