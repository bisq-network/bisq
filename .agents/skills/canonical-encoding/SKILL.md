---
name: canonical-encoding
description: "Create, change or review Bisq 1 canonical encodings: CanonicalSchema definitions and Canonical.encodeCanonical implementations (package bisq.common.encoding.canonical). Use it whenever a model class must produce deterministic bytes for a DAO state hash, a DAO proposal or blind-vote hash, a P2P storage key, a signature or a contract hash; when a class implements Canonical or defines a SCHEMA; when adding a field, a subclass or a ProtectedStoragePayload type; or when canonical bytes must be compared with protobuf output."
---

# Canonical Encoding in Bisq 1

Canonical encoding produces deterministic bytes for hash and signature preimages. The authoritative rules are in these documents; read the matching one before nontrivial work:

- [The encoding design](../../../docs/canonical-encoding/protobuf-compatible-deterministic-preimage.md): scope, schema concept, map ordering modes, malleability table and the per-type rules.
- [The hash use cases](../../../docs/canonical-encoding/protobuf-hash-use-cases.md): every production hash or signature path, its classes and its migration status, the legacy fallback and the blind-vote plaintext paths.
- [The offer extra-data map rules](../../../docs/canonical-encoding/offer-payload-extra-data-map.md): legacy key order of `OfferPayload` extra data and how to add a key.

## The Rules You Must Not Break

1. **Determinism.** The same object state produces the same bytes on every node. The bytes must not depend on the protobuf library, Java map iteration or anything except the encoded values.
2. **Protobuf parity.** The production paths were migrated from protobuf hashing in one step only because the canonical bytes equal `toProtoMessage().toByteArray()` for the accepted object shapes: same field numbers, types, omission of defaults and envelopes. A purpose-specific schema may deviate only where the legacy preimage did, for example `DaoState.STATE_HASH_CHAIN_SCHEMA`, which keeps only the last block and the legacy map order; such a deviation is stated in a comment and asserted by a test.
3. **No silent change of hashed bytes.** A changed schema changes DAO state and monitoring hashes, proposal and blind-vote hashes, P2P storage keys and sequence signatures, filter and alert signatures, offer hashes or payment account contract hashes. A change is acceptable only with byte identity for released data or an agreed activation (for example an activation block or schema version for DAO data). Do not decide an activation on your own.

The framework is not a general protobuf encoder and has no decoder. It supports only what Bisq preimages use. If a field needs a missing feature (for example `uint64`, `sint32`, `sint64`, `float`, fixed-width integers, `Any`, packed repeated types other than `int32`, maps with non-string keys, or explicit presence for a field whose present value can be the default), stop and report it; do not work around it. Adding a feature requires writer tests and an update of the design document.

## The Parts of the Framework

- `Canonical`: implemented by model classes. Implement `encodeCanonical(CanonicalEncoder canonicalEncoder)`, usually as `return canonicalEncoder.encode(this, SCHEMA);`. Callers use the default `encodeCanonical()`.
- `CanonicalSchema<T>`: an ordered list of fields built with `CanonicalSchema.<T>newBuilder()`. Every builder method takes the protobuf field number and a getter: `int32`, `uint32`, `int64`, `doubleField`, `bool`, `enumField`, `string`, `bytes`, `repeatedString`, `packedRepeatedInt32`, `compose`, `extend`, `oneof`, `repeatedCompose`, `mapStringToString` and `mapStringToCompose`. The builder rejects fields that are not in ascending field-number order.
- `CanonicalSchema.oneof(wrapperMessageName, fieldNumber, builder)`: wraps a schema in an envelope message such as `StoragePayload` or `PersistableEnvelope`.
- Schema version (`newBuilder(int version)`): metadata that selects canonical rules. It is not written to the bytes.
- `CanonicalEnum`: implemented by enums encoded with `enumField`. `getCode()` returns the protobuf enum number; it must not rely on the Java declaration order unless that is documented and tested for the enum.
- `CanonicalMapEntryIterator`: every map field names its entry order. `TreeMapIterator` gives sorted key order. `LegacyCollectorsToMapIterator` reproduces the legacy order of maps collected with `Collectors.toMap(...)` and is a documented compatibility mode, not a general choice.
- `CanonicalWriter`: low-level wire writer. Use schemas wherever possible; write by hand only for a delegating wrapper.

A typical class (`NodeAddress`):

```java
    public static final CanonicalSchema<NodeAddress> SCHEMA = CanonicalSchema.<NodeAddress>newBuilder()
            .string(1, NodeAddress::getHostName)
            .int32(2, NodeAddress::getPort)
            .build();

    @Override
    public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
        return canonicalEncoder.encode(this, SCHEMA);
    }
```

## Workflow: Add Canonical Encoding to a Class

1. **Read the protobuf message** in `proto/src/main/proto/pb.proto`: field numbers, types, `oneof` envelopes and nested messages.
2. **Read `toProtoMessage`** of the class and of its base classes: which envelope it writes, which values are set only when present, how values are converted (for example an enum written as its name into a `string` field, as in `Role`).
3. **List the fields** that `toProtoMessage` writes. Encode the fields, not getters that return derived or runtime values.
4. **Choose the pattern** from the table below.
5. **Write the schema** in ascending field-number order with exactly the protobuf types. Name the map entry order explicitly.
6. **Implement `encodeCanonical`** and add `Canonical` to the class, or rely on an interface that extends it, such as `ProtectedStoragePayload`.
7. **Write the tests** and run them: parity with protobuf bytes and a schema lock (see below).
8. **Update the documents** when you add a hash path, change its status in the use-case inventory, or add a framework feature or rule.

## Choose the Pattern

| What you see in the `.proto` and in `toProtoMessage` | Pattern | Example in Bisq 1 |
| --- | --- | --- |
| `Foo foo = n;` with a model class that has a schema | `compose(n, getter, Foo.SCHEMA)` | `BaseTxOutput.pubKeyScript` |
| `repeated Foo foos = n;` | `repeatedCompose(n, getter, Foo.SCHEMA)`, list order kept | `DaoState` blocks and cycles |
| Base message with `oneof message { Sub sub = n; ... }` | Base builder method plus `extend(n, getter, Sub.EXTENSION_SCHEMA)` per subtype; the getter returns `null` for other subtypes, so only the active extension is written | `Proposal.getProposalSchemaBuilder()` with `CompensationProposal` and the other proposal types |
| `toProtoMessage` returns an envelope such as `StoragePayload` | `CanonicalSchema.oneof("StoragePayload", n, builder)` | `Alert`, `Filter`, `TempProposalPayload` |
| `map<string, string>` | `mapStringToString(n, getter, entryIterator)` | extra data of `Alert` and `Proposal` with `TreeMapIterator.naturalOrder()` |
| Map whose legacy bytes depend on `Collectors.toMap` order | `LegacyCollectorsToMapIterator`, documented in the schema | `DaoState` hash-chain maps |
| A wrapper whose field is a payload that owns its preimage | Delegate with `CanonicalWriter.writeCompose(n, payload.encodeCanonical(canonicalEncoder))` | `P2PDataStorage.DataAndSeqNrPair` |

## Rules for Changing an Existing Encoding

- **Adding a protobuf field** to a canonical class: add it to the schema in the same change, at its field-number position, with parity tests for set and unset values.
- **Changing a protobuf field number or type** of a canonical class changes the hashes of all data that the last released version stores, signs or hashes with it: it is a protocol change. A field that only unreleased commits added may still change.
- **Changing a map entry order** changes the bytes for every map with more than one entry. Keep `LegacyCollectorsToMapIterator` where the legacy order is consensus relevant until an activation is agreed.
- **Adding an offer extra-data key**: follow [the offer extra-data map rules](../../../docs/canonical-encoding/offer-payload-extra-data-map.md).
- **Adding a subtype** of a type encoded with `extend` or `oneof`: add its extension to the base builder and to the parity tests.
- **Adding a `ProtectedStoragePayload` type**: it must implement `encodeCanonical` (the interface extends `Canonical`), and its storage key and sequence signature then hash those bytes.
- **Adding a hash or signature path**: use a `Canonical` model. `P2PDataStorage.get32ByteHash` still falls back to the deprecated `serializeForHash()` for payloads that do not implement `Canonical`; do not add new paths that depend on that fallback.

## Pitfalls

- **Defaults and presence.** Proto3 omits default scalars and empty singular strings and bytes, and the schema does the same. The framework has no explicit presence, so a nullable value that can also be the default cannot be distinguished.
- **Enum codes.** `getCode()` must return the protobuf number; one test per enum locks every value.
- **`double`.** Encoded as exact raw bits: positive zero is omitted, `-0.0` is written. Prefer integer fixed-point values for new fields.
- **Repeated strings.** Empty elements are kept. Keep a parity test with an empty element.
- **Blind-vote plaintexts.** `VoteWithProposalTxIdList` and `MeritList` are encrypted and later parsed with protobuf. Their canonical bytes must stay identical to protobuf; the switches `verifyBlindVoteEncryptedVotesSerialization` and `verifyBlindVoteEncryptedMeritListSerialization` compare them on live data.
- **Builders.** A shared base builder method must return a new builder on every call, as `Proposal.getBaseProposalSchemaBuilder()` does.
- **Decoding is out of scope.** Canonical bytes are produced from Bisq model objects only, never from parsed protobuf messages that may carry unknown fields.

## Testing

Follow the existing tests, for example `TxOutputCanonicalEncoderTest`, `DaoStateCanonicalEncoderTest` and `PaymentAccountPayloadCanonicalEncoderTest`:

- **Parity:** build the protobuf message, create the model with `fromProto`, and assert that `toProtoMessage().toByteArray()` equals `encodeCanonical(CanonicalEncoder.DEFAULT)`. Cover all fields set, nullable and default fields unset, every subtype, and empty and multi-entry maps and lists.
- **Schema lock:** assert the schema version, field numbers, field types and rules, so that an unintended schema change fails a test.
- **Order:** for maps, test entries whose insertion order differs from the selected order.

A successful decode alone cannot establish identical preimage bytes.

## Definition of Done

- Every field that `toProtoMessage` writes is in the schema, with the protobuf field number and type.
- Every map names its entry order, and legacy orders are documented in the schema.
- Parity and schema-lock tests pass for all shapes in scope.
- No DAO hash, storage key, signature or contract hash changed its bytes without an agreed activation.
- The use-case inventory and the design document reflect new hash paths, status changes and framework features.
