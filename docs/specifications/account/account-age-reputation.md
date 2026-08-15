# Bisq 1 account-age ownership proofs for Bisq 2

## Scope

This specification defines how a Bisq 1 payment-account owner proves control of an account-age
witness when importing that reputation into a Bisq 2 profile. It covers proof construction in Bisq
1 and authoritative verification by the Bisq 1 bridge. Bisq 2 profile binding, authorization and
scoring rules are specified in the Bisq 2 repository.

Signed-witness reputation uses the same ownership primitive plus the trust-chain requirements in
[signed-witness-reputation.md](signed-witness-reputation.md).

## Security invariant

An account-age witness may authorize reputation only for a requester who controls the DSA
signature key committed to that witness hash. Knowing the public witness hash and its public date is
not ownership proof.

The historical witness identity is:

```text
HASH160(accountInputDataWithSalt || ownerPublicKey)
```

where `HASH160` is SHA-256 followed by RIPEMD-160, `accountInputDataWithSalt` is the exact byte
sequence used when Bisq 1 created the witness, and `ownerPublicKey` is the encoded Bisq 1 DSA
signature public key. Verification must recompute this identity and compare it to the claimed
witness hash before returning an account age.

## Ownership protocol version 2

The version-2 proof carries:

- protocol version `2`;
- the target Bisq 2 profile id;
- the 20-byte account-age witness hash;
- the exact salted account-input byte sequence;
- the encoded owner DSA public key; and
- a SHA256withDSA signature by that owner key.

The signed message is the concatenation of the following values, each byte-array value prefixed by
a four-byte big-endian length and the version represented by a four-byte big-endian integer:

1. UTF-8 `BISQ2_ACCOUNT_AGE_REPUTATION_V2`;
2. protocol version `2`;
3. the lowercase UTF-8 Bisq 2 profile id;
4. witness hash;
5. salted account input; and
6. encoded owner public key.

The domain tag and length prefixes prevent a proof from being reused by another protocol or parsed
with ambiguous field boundaries. The profile id is part of the signed message so the proof cannot
be moved to another Bisq 2 identity.

Bisq 1 creates the export only when the selected account, current signature key and stored witness
recompute to the same hash and the proven owner key is not banned. The ban check happens before the
salted account input is read into the proof, avoiding disclosure for an unusable witness. The
witness date is not exported as a requester-authoritative value.

## Bridge verification

The bridge verifies the proof at the account-age-witness domain boundary. It must:

1. reject unsupported protocol versions and structurally invalid or oversized fields;
2. recompute `HASH160(accountInputDataWithSalt || ownerPublicKey)` and require an exact hash match;
3. verify the domain-separated signature with `ownerPublicKey`;
4. resolve the recomputed hash to an actual account-age witness in the Bisq 1 witness store;
5. reject the independently proven owner public key when it is banned by the current filter
   policy; and
6. return only the date stored in that authoritative witness.

An invalid proof is a request error. An unavailable or internally inconsistent bridge is an
infrastructure error. In either case Bisq 2 must not issue new reputation.

The historical date-only RPC does not prove ownership and must not be used for account-age
authorization. It fails closed after activation of this protocol.

## Privacy and disclosure

The proof discloses the salted account-input bytes and encoded account public key to the controlled
Bisq 2 oracle and its local Bisq 1 bridge. These bytes may contain sensitive payment-account
identifiers even though they include the account salt. The oracle already persists imported
account authorization material for abuse investigation, so operators must protect that private
state accordingly.

Avoiding this disclosure would require a different cryptographic protocol, such as a zero-knowledge
proof of the hash preimage. A self-signature over the public witness hash is not an acceptable
privacy-preserving substitute because it does not establish witness ownership.

## Compatibility and rollout

The prior JSON format and date-only bridge request are insecure authorization formats. They may
remain protobuf-parseable for mixed-version safety, but no upgraded bridge or oracle may use them to
issue account-age reputation. Existing Bisq 1 users must regenerate the version-2 JSON proof for the
chosen Bisq 2 profile.

The compatible Bisq 1 bridge must be deployed before account-age version-2 issuance is enabled on
Bisq 2 oracles. A new oracle talking to an older bridge fails closed because the ownership RPC is
unimplemented.

Signed-witness reputation is a separate protocol. It remains disabled until it reuses this witness
ownership proof and derives its sign date from records which individually satisfy the complete
Bisq 1 signed-witness trust-chain rules.

## Auditability of legacy authorization

The bundled Bisq 1 witness stores are source data, not a history of Bisq 2 authorization decisions.
An exploitation audit of the legacy missing-ownership-binding vulnerability cannot be performed from
`AccountAgeWitnessStore` or `SignedWitnessStore`: an attacker reused a legitimate public witness hash
and date, and the forged requester key, Bisq 2 profile id, oracle request and oracle-authorized result
were never written back into either Bisq 1 store. The DAO block height is likewise not a boundary for
those off-chain requests.

The resource audit on 2026-08-15 verified that the refreshed `1.10.5` account-age store is readable
and contains 6 172 unique, structurally valid witness records. This establishes resource integrity;
it does **not** establish that the legacy authorization path was unexploited.

Establishing exploitation or its absence requires Bisq 2 evidence from the vulnerable period: retained
oracle authorization requests or private persistence, published authorized reputation data which
retains the claimed witness identity, and profile-level reputation history. Each accepted legacy claim
must be reconciled to an ownership proof. If the retained authorized data contains only a profile id
and date, witness reuse cannot be reconstructed from it alone and the historical absence of exploitation
is unprovable. An audit must state that limitation rather than infer safety from clean Bisq 1 resources.
