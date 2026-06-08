# Canonical Encoding and Schema

Date: 2026-06-08

## Purpose

Bisq needs canonical bytes for consensus- and security-sensitive preimages, especially signature
preimages and DAO state monitoring for hash chains. The same logical object must produce the same
preimage bytes on every participating node that uses the same canonical schema version.

The canonical encoder must not depend on a protobuf implementation's serialization behavior for
those preimages. Changes in `protobuf-java`, generated message code, map iteration, unknown-field
handling, or other implementation details should not be able to change canonical bytes.

Bisq should also avoid two sources of truth for the encoding rules. A Java-defined
`CanonicalSchema` describes the fields, protobuf-compatible types, omission rules, nested schemas,
and ordering rules, and the generic encoder executes that schema. This keeps the schema definition
and executable implementation together instead of maintaining separate prose, descriptor, and manual
writer definitions that can drift.

## Why Protobuf-Compatible Encoding

Bisq could use any canonical byte format. A protobuf-compatible format is useful because Bisq
already uses `proto3` messages in persisted, network, and hashed data paths, and the current Java
protobuf output is the compatibility baseline for existing preimages. Reusing the protobuf wire
format used by `protobuf-java` helps avoid compatibility problems:

- existing field numbers and protobuf type choices can be reused;
- byte-level parity with current Java protobuf output can be tested where required;
- existing contributors already understand the model shape;
- activation can be scoped to schema versions instead of replacing the whole serialization stack.

The compatibility format is not "whatever any protobuf parser accepts". Bisq only needs the
protobuf features used by its canonical schemas, and every supported feature needs explicit rules
that remove protobuf malleability. The result is a limited protobuf-compatible subset that matches
current `protobuf-java` emission behavior where that behavior is required for compatibility, while
rejecting or leaving unsupported features that would otherwise add alternate encodings for the same
logical value.

## Scope

This design is limited to Bisq's canonical encoding requirements and Bisq's use of protobuf. It is
not a full protobuf encoder, a decoder, or a general canonical protobuf specification.
`CanonicalSchema` and `CanonicalWriter` should support only the protobuf features that Bisq needs
for canonical preimages. Each supported feature needs explicit schema rules and byte-level tests.
Unsupported protobuf features are rejected or left unimplemented until a Bisq model needs them.

There is intentionally no decoder in scope. The required operation is object-to-canonical-bytes for
hashing, monitoring, and signing. Decoding arbitrary protobuf-compatible bytes would introduce a
separate canonical-validation problem, including duplicate fields, unknown fields, non-minimal
varints, and merge behavior. That problem should be designed separately only if a concrete Bisq
requirement appears.

## Concept

The canonical encoder has two parts:

- `CanonicalSchema` describes what is encoded: message name, schema version, fields, protobuf type
  mapping, accessors, omission rules, nested schemas, and ordering rules.
- `CanonicalWriter` describes how supported schema elements become bytes: protobuf tags, varints,
  length-delimited values, nested message bytes, and repeated/map entry emission.

The schema is the source of truth. Serialization should iterate schema fields instead of maintaining
a parallel manual writer. Human-readable docs can summarize the schema, but they must not become a
second source of truth.

The encoder is generic because a schema can be written for any supported Bisq model. It is not
generic in the sense of accepting arbitrary protobuf descriptors or arbitrary parsed protobuf
messages. Canonical bytes should be produced from Bisq model objects whose semantics are already
known to the schema.

Schema version is metadata selecting canonical rules. It is not emitted as extra bytes unless a
schema explicitly defines a version field in the serialized object graph.

## Use Cases

DAO hash chain monitoring is the first consensus-sensitive use case. Nodes can encode the same DAO
state transition or hash-chain input with a versioned canonical schema and compare the resulting
hashes. A mismatch then points to a semantic or encoding divergence instead of incidental protobuf
serialization variation.

Signature preimages are a future use case. The signed bytes should be produced by a deterministic,
schema-defined encoder so signatures are not affected by unknown fields, duplicate fields, map
iteration order, or alternate protobuf encodings of the same logical values. That requires adding
the relevant signature schemas before using this encoder on those paths.

## Map Ordering and Legacy HashMap Compatibility

Protobuf map fields are encoded as repeated entry messages with key field `1` and value field `2`.
The protobuf wire format does not make map entry order canonical. Java's normal map iteration also
does not give a portable canonical order, and legacy Bisq paths may depend on `HashMap` iteration
behavior that should not be treated as a general-purpose rule.

Bisq should address that in two explicit modes:

- `LegacyCollectorsToMapIterator` reproduces the Java/protobuf behavior for legacy
  schemas that first collected map entries with default `Collectors.toMap(...)` and then copied the
  resulting `HashMap` into protobuf map fields. This is a compatibility mode and should be
  documented per schema; it is not a general-purpose legacy `HashMap` order.
- `TreeMapIterator` emits entries in sorted key order for schemas that can activate a
  canonical map order at a known activation block or schema version.

Schemas must choose a map iterator explicitly. The encoder should not rely on protobuf
deterministic serialization or default `Map` iteration order.

References:

- Protobuf encoding guide: https://protobuf.dev/programming-guides/encoding/
- Protobuf serialization is not canonical: https://protobuf.dev/programming-guides/serialization-not-canonical/
- Cosmos SDK ADR-027 deterministic protobuf serialization: https://docs.cosmos.network/sdk/latest/reference/architecture/adr-027-deterministic-protobuf-serialization
- Java `CodedOutputStream` API: https://protobuf.dev/reference/java/api-docs/com/google/protobuf/CodedOutputStream.html
- Java `CodedInputStream` API: https://protobuf.dev/reference/java/api-docs/com/google/protobuf/CodedInputStream.html

## ADR-027 Malleability Coverage

Cosmos SDK ADR-027 calls out protobuf malleability from field ordering, extra fields, default-value
presence, packed versus unpacked repeated numeric fields, and protobuf's 70-bit varint decoder
domain. Bisq covers those issues with the following rules and Bisq-specific scope choices:

| ADR-027 issue | Bisq canonical rule |
| --- | --- |
| Field order, duplicate singular fields, extra fields, and extra data | The schema emits known fields in ascending field-number order, singular fields at most once, and no unknown fields or trailing data. |
| Default-value presence | Default scalar values and empty singular strings/bytes are omitted for implicit-presence proto3 fields unless a schema explicitly defines presence. |
| Varint high-bit malleability from protobuf's `uint70` decoder domain | The writer uses typed field writers and emits the shortest valid varint for the selected field type. It never exposes a generic `uint70` writer. Negative `int32` and `int64` keep protobuf's standard 10-byte two's-complement encoding. |
| Boolean varints | Singular implicit-presence `false` is omitted; emitted boolean values are only `0` when explicit presence requires it or `1` for `true`. |
| Packed versus unpacked repeated numeric fields | The schema supports packed repeated `int32` and emits exactly one proto3 packed segment in list order. Other packed numeric element types remain unsupported until a Bisq schema needs them. |
| Maps | ADR-027 version 1 rejects maps. Bisq supports maps only when a schema declares an explicit entry order mode and treats duplicate mapped keys as non-canonical. |

## General Rules

- The schema, not ad hoc writer code, is the source of truth for field number, field name, field kind, omission rule, nested compose/extend schema, and ordering rule.
- Fields are emitted in ascending field-number order unless a schema explicitly defines a repeated or map ordering rule.
- Unknown fields are never emitted in canonical preimage bytes.
- Raw extra fields and trailing bytes are never emitted.
- A singular field is emitted at most once.
- Every tag, length prefix, and integer value uses the shortest valid varint form for the selected field type, except negative `int32` and `int64` values use protobuf's standard 10-byte two's-complement varint form.
- Nested messages are emitted as length-delimited canonical bytes produced by their own schema.
- Map fields must declare an explicit order mode.

## Malleability Table

| Field type or construct | Java protobuf behavior used in Bisq                                                                                                                                                                                                                                                  | Other protobuf-allowed or cross-language versions | Canonical Bisq restriction | If Java behavior is not clear enough |
| --- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| --- | --- | --- |
| Field tag | Java writes `(field_number << 3) \| wire_type` as a varint.                                                                                                                                                                                                                          | A parser may encounter non-minimal varint encodings of the same tag if bytes are hand-crafted. | Emit only shortest-form tag varints. Reject or never construct non-minimal tag encodings. | Add low-level writer tests for multi-byte tag boundaries and no API for raw tag bytes. |
| Varint numeric domain | Java writers receive typed Java values and write the protobuf varint for that field type.                                                                                                                                                                                            | Protobuf decoders can accept up to 10-byte varints as 70-bit unsigned integers, then cast them down to `int32`, `uint32`, `int64`, `uint64`, `sint32`, `sint64`, or `bool`, allowing unused high bits to carry alternate encodings. | Canonical writer APIs must be typed to the schema field type and never emit a generic `uint70` value. Values must fit the selected type before encoding; a future canonical reader must reject high unused bits and over-wide varints except protobuf's required negative `int32` sign extension. | Add low-level tests for max canonical 32-bit and 64-bit varints, over-wide encodings, and negative `int32`/`int64` boundary values if a canonical reader is added. |
| Field order | Generated Java serialization emits known fields in generated field-number order for normal messages.                                                                                                                                                                                 | Protobuf parsers accept fields in any order. Serialization order is explicitly not canonical across implementations. | Emit fields in ascending schema order unless a repeated/map schema says otherwise. | Keep order in `CanonicalSchema`; constructor rejects non-ascending singular field declarations. |
| Unknown fields | Java generated messages can preserve and re-emit unknown fields when parsing arbitrary input. Bisq-built model objects do not need unknown fields in canonical preimages.                                                                                                            | Unknown fields may be serialized before/after known fields depending on implementation and version. | Canonical serializers never emit unknown fields. | Do not serialize from parsed protobuf messages directly; serialize from Bisq model objects only. |
| Duplicate singular scalar fields | Java generated builders store one value; when parsing duplicate singular fields, protobuf merge rules keep the last scalar value.                                                                                                                                                    | Wire bytes may contain the same singular field multiple times and parse to the same logical value. | Emit each singular scalar field at most once. | Add schema tests that each field number appears once unless field type is explicitly repeated/map. |
| Duplicate singular message fields | Java parsing merges duplicate message fields. Serialization of a built object emits the merged message once.                                                                                                                                                                         | Multiple wire instances of the same message field can parse to one logical message. | Emit each singular message field at most once, with nested canonical bytes. | Treat duplicate message fields as non-canonical if a future reader/verifier is added. |
| `int32` | Java writes positive values as shortest varint; negative `int32` values are sign-extended and take 10 bytes.                                                                                                                                                                         | Hand-crafted bytes can encode positive values with non-minimal varints. `sint32` would use zigzag, but `int32` does not. | Positive `int32`: shortest varint. Negative `int32`: standard 10-byte two's-complement varint. Omit default `0` unless schema says otherwise. | Keep separate writer methods for `int32` and `sint32` if `sint32` is ever introduced. |
| `int64` | Java writes `int64` as shortest varint for non-negative values; negative values take 10 bytes.                                                                                                                                                                                       | Hand-crafted bytes can use non-minimal positive varints. | Shortest varint for non-negative values. Negative values use the standard 10-byte two's-complement varint. Omit default `0` unless schema says otherwise. | Low-level tests should cover `0`, `1`, `127`, `128`, max positive, and `-1`. |
| `uint32` / `uint64` | Java writes shortest unsigned varints.                                                                                                                                                                                                                                               | Non-minimal varints can parse to the same unsigned value. | `uint32` is supported for DAO proposal version fields and uses shortest unsigned varints, omitting default `0` unless schema says otherwise. `uint64` remains unsupported until a Bisq canonical schema needs it. | Add `uint64` writer methods before serializing a model field that uses 64-bit unsigned protobuf integer semantics. |
| `sint32` / `sint64` | Not currently supported. Java would zigzag-encode then varint.                                                                                                                                                                                                                       | Confusing `int*` and `sint*` changes bytes for negative values. | Only add with explicit schema type and tests. Use shortest varint after zigzag. | Disallow until needed. |
| `bool` | Java writes `false` as default omitted and `true` as varint `1` when present.                                                                                                                                                                                                        | Parsers commonly treat any nonzero varint as true. | Write only `0` or `1`; omit `false` for singular implicit-presence fields unless explicit presence is required. | A future canonical reader should reject nonzero values other than `1`. |
| Enum | Java writes the numeric enum value. Java declaration order only matches protobuf order for some enums.                                                                                                                                                                               | Proto3 can preserve unknown enum numeric values. Different languages expose unknown enum values differently. | Use schema-defined enum codes. Ordinal fallback is allowed only when documented and tested for that enum. Omit code `0` unless schema says otherwise. | Add one test per enum that locks every value in scope. |
| Fixed-width integers | Not currently supported. Java fixed32/fixed64 use little-endian fixed-width bytes.                                                                                                                                                                                                   | No varint malleability, but signed/unsigned interpretation and endian mistakes are possible. | Disallow until needed; if added, schema must name fixed32/fixed64/sfixed32/sfixed64 explicitly. | Add byte-level fixtures for endian order. |
| Float / double | Java writes `double` as fixed64 little-endian bytes and omits only raw-bit positive zero.                                                                                                                                                                                            | NaN has many bit patterns; `0.0` and `-0.0` can be semantically surprising. | `double` is supported only as exact `Double.doubleToRawLongBits` output; raw-bit positive zero is omitted and `-0.0` is emitted. `float` remains unsupported. | Prefer integer fixed-point representations for new protocol fields. |
| String | Java serializes `String` to UTF-8 bytes and generated proto3 string fields require valid UTF-8.                                                                                                                                                                                      | Different sources can normalize Unicode text differently before it reaches protobuf. Invalid UTF-8 may exist in hand-crafted length-delimited bytes for non-string fields. | Serialize Java `String` UTF-8 bytes exactly; no Unicode normalization; omit empty singular strings unless schema says otherwise. | If user-controlled Unicode ever affects consensus, document whether normalization is intentionally absent. |
| Bytes | Java writes the exact byte array or `ByteString` bytes.                                                                                                                                                                                                                              | Bytes are opaque; equivalent higher-level encodings are application-specific. | Write bytes exactly as stored. Omit empty singular bytes unless schema says otherwise. | Do not reinterpret byte fields as nested messages unless schema defines a message field. |
| Length-delimited message | Java writes the nested message size as a varint and then nested bytes.                                                                                                                                                                                                               | Non-minimal length varints may parse to the same length. Duplicate message fields may merge. | Length prefix is shortest-form varint. Nested bytes must be the nested schema's canonical serialization. | Add tests for zero-length nested messages only if a schema can actually emit one. |
| Repeated non-packed fields | Java emits each element in list order for non-packed repeated fields.                                                                                                                                                                                                                | Repeated elements can be interleaved with other fields and still parse into the same list order in many cases. | Emit repeated fields at their schema position, preserving list order. Do not interleave with other fields. | Schema should mark the field repeated and define element order explicitly. |
| Repeated string/bytes | Java emits each element as a length-delimited field, including empty elements when present in the repeated list.                                                                                                                                                                     | Empty repeated elements are distinct from absence but easy to accidentally drop in custom writers. | Preserve every element in list order, including zero-length elements. | Keep a parity test with an empty repeated string element. |
| Packed repeated numeric fields | Proto3 numeric repeated fields are usually packed by default in generated code.                                                                                                                                                                                                      | Parsers usually accept packed and unpacked encodings, and multiple packed segments may concatenate. ADR-027 requires packed encoding for repeated scalar numeric fields. | `packedRepeatedInt32` emits one packed segment at the schema field position and preserves every element, including zero values. Unpacked encodings and multiple packed segments are not emitted by canonical writers. Other packed numeric element types remain unsupported until needed. | Add element-specific writer tests before any additional packed numeric field type enters a canonical preimage. |
| Map fields | Java map serialization has implementation-specific ordering unless deterministic serialization is enabled; deterministic map ordering is still not guaranteed canonical across all languages for every key type. Some legacy Bisq paths also have explicit `HashMap` order hotspots. | Maps are repeated entry messages; wire order may vary, duplicate keys may parse with last value winning, and deterministic ordering can differ by language/key type. | Encode supported map fields as repeated entries with key field `1` and value field `2`; each schema must pass an explicit entry iterator such as `LegacyCollectorsToMapIterator` for legacy default-`Collectors.toMap(...)` paths or `TreeMapIterator` for sorted order. Duplicate mapped keys are non-canonical. | Do not rely on protobuf deterministic map serialization. Implement and test each chosen order mode before using it in a schema. |
| Oneof | Java builder stores one selected case and generated serialization emits that one field.                                                                                                                                                                                              | Wire bytes can contain multiple fields from the same oneof; parsers normally keep the last one seen. | Schema must emit exactly the selected oneof field for the Java object type, or none if allowed. Multiple oneof fields are non-canonical. | Add explicit unsupported-path tests for oneof alternatives outside the supported schema graph. |
| Default scalar values | Proto3 generated Java omits implicit-presence default scalar values.                                                                                                                                                                                                                 | Explicit `optional` fields and wrapper messages can preserve presence for defaults. | Omit default scalar values for implicit-presence proto3 fields. If presence is needed, schema must mark it explicitly. | Do not infer presence from Java default values. |
| Empty singular string/bytes | Java generated proto3 serialization omits empty singular strings and bytes.                                                                                                                                                                                                          | Length-delimited zero-length fields can often parse to the same default value. | Omit empty singular string/bytes fields. | Use repeated fields if empty elements must be represented. |
| Groups | Deprecated and not supported.                                                                                                                                                                                                                                                        | Protobuf wire format supports start/end groups. | Disallow groups in canonical preimage schemas. | Fail schema construction if group support is ever requested. |
| Extensions / MessageSet / Any | Not supported.                                                                                                                                                                                                                                                                       | These can carry arbitrary nested type URLs, extension fields, or unknown data. | Disallow unless a future schema defines a narrow canonical form. | Prefer explicit message fields. |

## Schema Implications

Each `CanonicalSchema` should define:

- schema version;
- field number;
- field type;
- field value accessor;
- omission rule;
- nested compose or extend schema, if any;
- repeated/map ordering rule;
- oneof selection rule, if any.
