# Protobuf Hash Use Cases

Date: 2026-06-09

## Purpose

This document inventories production code paths where Bisq hashes bytes that are
currently produced from protobuf serialization, either directly through
`serializeForHash()` or through a wrapper that delegates to it. These hashes are
security-sensitive because they are used for signatures, P2P storage keys,
consensus payload identifiers, or hash-chain comparisons.

The canonical encoding goal is to make these preimages explicit and owned by
Bisq instead of depending on protobuf runtime serialization behavior.

## Use Cases

| Use case | Hash/signature path | Involved classes | Canonical migration status |
| --- | --- | --- | --- |
| DAO state hash chain | `DaoState.getSerializedStateForHashChain()` is hashed by `DaoStateMonitoringService` | `DaoState`, DAO blockchain and governance state model classes under `bisq.core.dao.state.model` | Already uses schema-based canonical encoding in `DaoState.encodeCanonicalForStateHashChain`. |
| DAO proposal payload hash | `ProposalConsensus.getHashOfPayload` and `ProposalPayload` hash `Proposal.serializeForHash()` | `Proposal`, `CompensationProposal`, `ReimbursementProposal`, `ChangeParamProposal`, `RoleProposal`, `ConfiscateBondProposal`, `GenericProposal`, `RemoveAssetProposal`, `ProposalPayload` | Migrated by making `serializeForHash()` use canonical bytes for `Canonical` models. Proposal schemas already exist. |
| DAO proposal-state monitoring | `ProposalStateMonitoringService` hashes `new MyProposalList(proposals).serializeForHash()` | `MyProposalList`, `Proposal` and concrete proposal subclasses | Migrated by adding a canonical schema for `MyProposalList`. |
| DAO blind-vote payload hash | `BlindVotePayload` hashes `BlindVote.serializeForHash()` | `BlindVote`, `BlindVotePayload` | Migrated by adding a canonical schema for `BlindVote`. |
| Vote-reveal blind-vote list hash | `VoteRevealConsensus.getHashOfBlindVoteList` concatenates `BlindVote.serializeForHash()` bytes | `VoteRevealConsensus`, `BlindVote` | Migrated through the new `BlindVote` canonical schema. |
| DAO blind-vote-state monitoring | `BlindVoteStateMonitoringService` hashes `new MyBlindVoteList(blindVotes).serializeForHash()` | `MyBlindVoteList`, `BlindVote` | Migrated by adding a canonical schema for `MyBlindVoteList`. |
| Bonded role hash | `Role` constructor hashes `Role.serializeForHash()` | `Role`, `BondedRoleType` | Migrated by making `serializeForHash()` use canonical bytes for `Canonical` models. Role schema already exists. |
| Burning Man accounting oracle signatures | `AccountingNode.getSha256Hash` hashes `AccountingBlock.serializeForHash()` for single blocks and block collections before signing or verifying | `AccountingNode`, `AccountingBlock`, `AccountingTx`, `AccountingTxOutput`, accounting network messages | Migrated by adding canonical schemas for the accounting block model. |
| Developer filter signature | `FilterManager` signs `Sha256Hash.of(filter.serializeForHash())` | `FilterManager`, `Filter`, `PaymentAccountFilter` | Migrated by adding canonical support for `double` and packed repeated `int32` fields, then adding canonical schemas for `Filter` and `PaymentAccountFilter` with protobuf parity tests. |
| P2P protected storage keys and sequence signatures | `P2PDataStorage.get32ByteHash` hashes `NetworkPayload.serializeForHash()` and signs `DataAndSeqNrPair` hashes | `P2PDataStorage`, `DataAndSeqNrPair`, `ProtectedStorageEntry`, `ProtectedMailboxStorageEntry`, protected payload implementations | Migrated by adding a canonical `DataAndSeqNrPair` wrapper and schemas for the current `StoragePayload` variants: alert, dispute-agent, filter, mailbox, offer, and temp-proposal payloads. `DataAndSeqNrPair` intentionally delegates its payload field to `ProtectedStoragePayload.serializeForHash()`, so each storage payload owns its canonical hash preimage. |
| Offer payload hashes | `OfferPayloadBase.getHash()` and `OfferPayload.getHash()` hash offer payload serialization | `OfferPayloadBase`, `OfferPayload`, `BsqSwapOfferPayload`, `OfferPayloadExtraDataMap`, `ProofOfWork`, `NodeAddress`, `PubKeyRing` | Migrated by adding canonical schemas for both offer storage wrappers and nested offer value types, preserving existing extra-data map iteration order with protobuf parity tests. |
| Payment account contract hash | `PaymentAccountPayload.getHashForContract()` hashes payment account payload serialization | `PaymentAccountPayload` and all concrete payment-account payload subclasses | Migrated by adding a complete payment-account canonical schema hierarchy. `getHashForContract()` now hashes `serializeForHash()`, and parity tests cover protobuf-loaded and locally-created payloads. |
| Temp proposal protected payload | P2P storage hashes `TempProposalPayload.serializeForHash()` through protected storage | `TempProposalPayload`, `Proposal`, `P2PDataStorage` | Migrated by adding a canonical `TempProposalPayload` storage wrapper schema over the existing `Proposal` canonical schema. |

## Intentional Legacy Protobuf Fallbacks

`Proto.serializeForHash()` and `ExcludeForHashAwareProto.serializeForHash()`
return canonical bytes only when the object implements `Canonical`. The
current inventory has no known production `serializeForHash()` hash preimage
that intentionally remains on the legacy protobuf fallback. Keep this section as
the review point for future non-`Canonical` payloads before they are used in a
signature, storage-key, contract-hash, or consensus-hash preimage.

## Adjacent Serialization-Sensitive Payloads

The following path is intentionally not classified as a remaining
`serializeForHash()` fallback, but it still depends on protobuf serialization
before a consensus hash is produced.

### Blind Vote Encrypted Plaintexts

`MyBlindVoteListService.getEncryptedVotes()` now serializes
`VoteWithProposalTxIdList` with canonical `serializeForHash()`, optionally
compares those bytes with the legacy protobuf bytes under
`verifyBlindVoteEncryptedVotesSerialization`, and passes the canonical plaintext
to `BlindVoteConsensus.getEncryptedVotes(byte[], SecretKey)`.
`MyBlindVoteListService.getOpReturnData()` then hashes the encrypted vote bytes
for the blind-vote op-return commitment.

`MyBlindVoteListService.getEncryptedMeritList()` now serializes `MeritList`
with canonical `serializeForHash()`, optionally compares those bytes with the
legacy protobuf bytes under
`verifyBlindVoteEncryptedMeritListSerialization`, and passes the canonical
plaintext to `BlindVoteConsensus.getEncryptedMeritList(byte[], SecretKey)`.
The resulting ciphertext is stored as `BlindVote.encryptedMeritList` and
included as bytes in the canonical `BlindVote` hash preimage.

These encrypted plaintext paths differ from the migration targets above because
Bisq does not directly hash the plaintext. The hash preimage is ciphertext.
Still, the ciphertext and the op-return hash depend on the plaintext
serialization format, and vote reveal decrypts those bytes and parses them with
protobuf parsers. The current migration relies on canonical bytes being
byte-identical to protobuf bytes for the supported plaintext shapes, with
verification switches available for live resync checks.

## Non-Protobuf Hashes

The following nearby hash or signature paths do not hash serialized protobuf
bytes and are not part of this migration:

- account-age witness hashes that concatenate account input data and salts;
- dispute and trade contract hashes over JSON or text;
- Bitcoin transaction signatures;
- proof-of-work hashes over explicit byte/string preimages;
- trade-statistics hashes based on JSON.

## Compatibility Notes

Switching a hash preimage can change P2P keys, signature verification, or
consensus identifiers. A path can be migrated in one step only when the canonical
schema is byte-compatible with the current protobuf output for the accepted
object shapes, or when the protocol has an explicit activation and legacy
verification plan.

This change applies canonical encoding only to bounded model graphs with existing
or added schemas and focused parity tests. The larger P2P protected-payload and
payment-account hierarchies remain documented follow-up work because they need a
broader schema and compatibility surface.
