# OfferPayload ExtraDataMap Ordering

## Purpose

`OfferPayload.extraDataMap` is part of the protobuf bytes used for offer payloads. Historical clients populated that map with `java.util.HashMap`. Protobuf Java serializes map fields in the iteration order of its internal map when normal, non-deterministic serialization is used, so changing the Java map iteration order can change the serialized bytes.

`OfferPayloadExtraDataMap` exists to make the order explicit and stable while preserving byte compatibility for the finite set of offer extra-data keys used by existing producer code.

## Supported Keys

Only the following keys are supported:

- `capabilities`
- `referralId`
- `xmrAutoConf`
- `accountAgeWitnessHash`
- `cashByMailExtraInfo`
- `f2fExtraInfo`
- `f2fCity`

Any attempt to add another key must fail immediately. This is intentional. Supporting a new key requires updating `OfferPayloadExtraDataMap`, this document, and the ordering tests.

Values must be non-null. Protobuf map fields cannot serialize null values.

## Legacy Order

The canonical order is:

1. `capabilities`
2. `referralId`
3. `xmrAutoConf`
4. `accountAgeWitnessHash`
5. `cashByMailExtraInfo`
6. `f2fExtraInfo`
7. `f2fCity`

This order matches Java 21 `HashMap` iteration for all non-empty subsets of the supported keys when the keys are inserted by the current offer producer order:

1. `accountAgeWitnessHash`
2. `referralId`
3. `f2fCity`
4. `f2fExtraInfo`
5. `cashByMailExtraInfo`
6. `capabilities`
7. `xmrAutoConf`

The same behavior is expected for Java 11 for this finite set because these keys stay in the default small-table `HashMap` bucket layout and do not trigger resizing or treeification.

## Construction Modes

`OfferPayloadExtraDataMap` has two ordering modes:

- Local construction: `new OfferPayloadExtraDataMap()` stores entries in a `LinkedHashMap` and reorders the map into the canonical legacy order after every `put` or `putAll`. Caller insertion order does not affect serialized bytes.
- Protobuf construction: `new OfferPayloadExtraDataMap(proto.getExtraDataMap())` validates the finite key set but preserves the protobuf map iteration order. This allows already serialized payloads to be read and written again without changing their extra-data entry order.

If protobuf-loaded data is locally modified, the code must decide whether it is preserving existing bytes or intentionally rewriting the payload. Current capability-update code copies entries into a locally constructed `OfferPayloadExtraDataMap`, then writes the updated value, so the result uses canonical legacy order.

## Constraints

- Do not use raw `HashMap`, `TreeMap`, or `Map.of` as the final map passed to `protobuf.OfferPayload.Builder.putAllExtraData`.
- Do not add a supported key without updating the canonical order and adding tests that compare against the legacy `HashMap` producer behavior.
- Do not assume arbitrary insertion orders can be reproduced from a single canonical order. Java `HashMap` keeps insertion order inside colliding buckets. The compatibility guarantee is for current producer paths and for preserving already-loaded protobuf order.
- Do not use deterministic protobuf serialization for byte compatibility with historical offer payloads. Deterministic serialization sorts string map keys lexicographically, which differs from the legacy order.
- Keep `OfferPayloadExtraDataMapTest` focused on byte equality. The tests must cover every non-empty supported-key subset and must compare protobuf bytes against a real Java `HashMap` built in the current producer insertion order.

## Adding A New Key

Before adding a new key:

1. Identify the producer path and exact insertion point.
2. Re-evaluate Java 11 and Java 21 `HashMap` iteration order for every supported subset that can be produced.
3. Update the canonical order in `OfferPayloadExtraDataMap`.
4. Update the supported-key list in this document.
5. Extend `OfferPayloadExtraDataMapTest` so it compares the new finite key set against the legacy producer behavior.
6. Confirm normal protobuf serialization still emits the expected bytes.
