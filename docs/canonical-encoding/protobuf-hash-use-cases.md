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
| P2P protected storage keys and sequence signatures | `P2PDataStorage.get32ByteHash` hashes `NetworkPayload.serializeForHash()` and signs `DataAndSeqNrPair` hashes | `P2PDataStorage`, `DataAndSeqNrPair`, `ProtectedStorageEntry`, `ProtectedMailboxStorageEntry`, protected payload implementations | Partially migrated by adding a canonical `DataAndSeqNrPair` wrapper and schemas for alert, filter, offer, and temp-proposal protected payloads. `DataAndSeqNrPair` intentionally delegates its payload field to `ProtectedStoragePayload.serializeForHash()`, so migrated payloads contribute canonical bytes and the unmigrated payloads listed below still contribute legacy protobuf bytes. |
| Offer payload hashes | `OfferPayloadBase.getHash()` and `OfferPayload.getHash()` hash offer payload serialization | `OfferPayloadBase`, `OfferPayload`, `BsqSwapOfferPayload`, `OfferPayloadExtraDataMap`, `ProofOfWork`, `NodeAddress`, `PubKeyRing` | Migrated by adding canonical schemas for both offer storage wrappers and nested offer value types, preserving existing extra-data map iteration order with protobuf parity tests. |
| Payment account contract hash | `PaymentAccountPayload.getHashForContract()` hashes payment account payload serialization | `PaymentAccountPayload` and all concrete payment-account payload subclasses | Not migrated in this change. This is a large oneof hierarchy and needs a complete schema set with contract-hash parity vectors. |
| Temp proposal protected payload | P2P storage hashes `TempProposalPayload.serializeForHash()` through protected storage | `TempProposalPayload`, `Proposal`, `P2PDataStorage` | Migrated by adding a canonical `TempProposalPayload` storage wrapper schema over the existing `Proposal` canonical schema. |

## Intentional Legacy Protobuf Fallbacks

`Proto.serializeForHash()` and `ExcludeForHashAwareProto.serializeForHash()`
return canonical bytes only when the object implements `Canonical`. The
following hash preimages still intentionally use legacy protobuf bytes until
their complete object graph has schemas, protobuf parity vectors, and a
compatibility plan for the affected signatures or identifiers.

### P2P Protected Storage Payloads

Migrated `StoragePayload` variants are `Alert`, `Filter`, `OfferPayload`,
`BsqSwapOfferPayload`, and `TempProposalPayload`. The remaining protected
storage variants still use legacy protobuf bytes when they are nested into
`DataAndSeqNrPair` or hashed directly as protected-storage keys:

- `Arbitrator`: not migrated yet because the payload is deprecated but can still
  appear in old persisted or network data. Any migration should be handled with
  the full `DisputeAgent` schema set and historical byte-compatibility vectors.
- `Mediator`: not migrated yet because it shares the `DisputeAgent` model with
  `RefundAgent` and the deprecated `Arbitrator`, including nested `NodeAddress`,
  `PubKeyRing`, repeated language codes, optional email/info fields, and
  deprecated `extraDataMap` validation. It should be migrated together with the
  other dispute-agent storage variants.
- `RefundAgent`: not migrated yet for the same `DisputeAgent` reason as
  `Mediator`, plus it is capability-gated. A migration needs parity coverage for
  the shared fields and the `StoragePayload.refund_agent` wrapper.
- `MailboxStoragePayload`: not migrated yet because it contains encrypted
  mailbox messages through `PrefixedSealedAndSignedMessage`, separate sender and
  owner keys for add/remove authorization, and an optional TTL in
  `extraDataMap`. It needs schemas for the nested encrypted message envelope and
  explicit compatibility checks for mailbox add/remove signatures and storage
  keys.

### Payment Account Contract Hashes

`PaymentAccountPayload.getHashForContract()` still hashes legacy protobuf bytes
for the complete payment-account hierarchy. This is not just storage-key
compatibility: the hash is exchanged and checked during the trade protocol, so a
partial migration would risk maker/taker contract-hash mismatches.

The unmigrated hierarchy includes the abstract/wrapper classes
`PaymentAccountPayload`, `CountryBasedPaymentAccountPayload`,
`BankAccountPayload`, `IfscBasedAccountPayload`, and `AssetsAccountPayload`, plus
these concrete payload classes:

- `AchTransferAccountPayload`
- `AdvancedCashAccountPayload`
- `AliPayAccountPayload`
- `AmazonGiftCardAccountPayload`
- `AustraliaPayidAccountPayload`
- `BizumAccountPayload`
- `BsqSwapAccountPayload`
- `CapitualAccountPayload`
- `CashAppAccountPayload`
- `CashByMailAccountPayload`
- `CashDepositAccountPayload`
- `CelPayAccountPayload`
- `ChaseQuickPayAccountPayload`
- `ClearXchangeAccountPayload`
- `CryptoCurrencyAccountPayload`
- `DomesticWireTransferAccountPayload`
- `F2FAccountPayload`
- `FasterPaymentsAccountPayload`
- `HalCashAccountPayload`
- `ImpsAccountPayload`
- `InstantCryptoCurrencyPayload`
- `InteracETransferAccountPayload`
- `JapanBankAccountPayload`
- `MercadoPagoAccountPayload`
- `MoneyBeamAccountPayload`
- `MoneyGramAccountPayload`
- `MoneseAccountPayload`
- `NationalBankAccountPayload`
- `NeftAccountPayload`
- `NequiAccountPayload`
- `OKPayAccountPayload`
- `PaxumAccountPayload`
- `PayseraAccountPayload`
- `PaytmAccountPayload`
- `PerfectMoneyAccountPayload`
- `PixAccountPayload`
- `PopmoneyAccountPayload`
- `PromptPayAccountPayload`
- `RevolutAccountPayload`
- `RtgsAccountPayload`
- `SameBankAccountPayload`
- `SatispayAccountPayload`
- `SbpAccountPayload`
- `SepaAccountPayload`
- `SepaInstantAccountPayload`
- `SpecificBanksAccountPayload`
- `StrikeAccountPayload`
- `SwiftAccountPayload`
- `SwishAccountPayload`
- `TikkieAccountPayload`
- `TransferwiseAccountPayload`
- `TransferwiseUsdAccountPayload`
- `UpholdAccountPayload`
- `UpiAccountPayload`
- `USPostalMoneyOrderAccountPayload`
- `VenmoAccountPayload`
- `VerseAccountPayload`
- `WeChatPayAccountPayload`
- `WesternUnionAccountPayload`

The migration blocker is the multi-level protobuf oneof shape, deprecated
fields, and the `excludeFromJsonDataMap` salt/metadata map. Migrating this path
should be done as one complete hierarchy with contract-hash parity vectors for
each concrete payload type.

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
