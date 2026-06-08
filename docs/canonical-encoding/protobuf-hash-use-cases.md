# Protobuf Hash Use Cases

Date: 2026-06-08

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
| P2P protected storage keys and sequence signatures | `P2PDataStorage.get32ByteHash` hashes `NetworkPayload.serializeForHash()` and signs `DataAndSeqNrPair` hashes | `P2PDataStorage`, `DataAndSeqNrPair`, `ProtectedStorageEntry`, `ProtectedMailboxStorageEntry`, protected payload implementations | Partially covered when nested payloads implement `Canonical`. Full migration needs schemas for all protected payload variants and dual-key compatibility planning. |
| Offer payload hashes | `OfferPayloadBase.getHash()` and `OfferPayload.getHash()` hash offer payload serialization | `OfferPayloadBase`, `OfferPayload`, `BsqSwapOfferPayload`, `OfferPayloadExtraDataMap`, `ProofOfWork`, `NodeAddress`, `PubKeyRing` | Migrated by adding canonical schemas for both offer storage wrappers and nested offer value types, preserving existing extra-data map iteration order with protobuf parity tests. |
| Payment account contract hash | `PaymentAccountPayload.getHashForContract()` hashes payment account payload serialization | `PaymentAccountPayload` and all concrete payment-account payload subclasses | Not migrated in this change. This is a large oneof hierarchy and needs a complete schema set with contract-hash parity vectors. |
| Temp proposal protected payload | P2P storage hashes `TempProposalPayload.serializeForHash()` through protected storage | `TempProposalPayload`, `Proposal`, `P2PDataStorage` | Migrated by adding a canonical `TempProposalPayload` storage wrapper schema over the existing `Proposal` canonical schema. |

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
