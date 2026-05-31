# Protobuf-Compatible Deterministic Preimage Rules

Date: 2026-05-31

## Scope

Bisq currently uses `proto3` messages in multiple persisted, network, and hashed data paths, and pins `protobuf-java` and `protoc` to version `3.25.8` in `gradle/libs.versions.toml`.

The deterministic preimage format intentionally uses protobuf-compatible wire bytes because that gives low transition cost from existing Bisq serialization paths. The compatibility format is not "whatever any protobuf parser accepts". It is a canonical subset of protobuf-compatible output that matches the current Java protobuf emission behavior where that behavior is clear, and adds explicit Bisq rules where protobuf parsing allows multiple equivalent encodings.

This is not a full protobuf encoder, decoder, or canonical protobuf specification. `CanonicalSchema` and `CanonicalWriter` should support only the protobuf features that Bisq actually needs for canonical preimages, and each supported feature must have explicit schema rules plus byte-level tests. Unsupported protobuf features are rejected or left unimplemented until a Bisq model needs them.

This document is the checklist for schema definitions such as `CanonicalSchema`. It should be kept broader than the initial trigger use case so new canonical preimage schemas do not accidentally introduce malleability.

References:

- Protobuf encoding guide: https://protobuf.dev/programming-guides/encoding/
- Protobuf serialization is not canonical: https://protobuf.dev/programming-guides/serialization-not-canonical/
- Java `CodedOutputStream` API: https://protobuf.dev/reference/java/api-docs/com/google/protobuf/CodedOutputStream.html
- Java `CodedInputStream` API: https://protobuf.dev/reference/java/api-docs/com/google/protobuf/CodedInputStream.html

## General Rules

- The schema, not ad hoc writer code, is the source of truth for field number, field name, field kind, omission rule, nested compose/extend schema, and ordering rule.
- Fields are emitted in ascending field-number order unless a schema explicitly defines a repeated or map ordering rule.
- Unknown fields are never emitted in canonical preimage bytes.
- A singular field is emitted at most once.
- Schema version is metadata selecting the canonical rules. It is not emitted as extra bytes unless a schema explicitly defines a version field in the serialized object graph.
- Every tag, length prefix, and integer value uses the shortest valid varint form for the selected field type, except negative `int32` and `int64` values use protobuf's standard 10-byte two's-complement varint form.
- Nested messages are emitted as length-delimited canonical bytes produced by their own schema.
- Map fields must declare an explicit order mode. The only allowed legacy behavior should be named as such, for example `LegacyHashMapOrder`.

## Malleability Table

| Field type or construct | Java protobuf behavior used in Bisq | Other protobuf-allowed or cross-language versions | Canonical Bisq restriction | If Java behavior is not clear enough |
| --- | --- | --- | --- | --- |
| Field tag | Java writes `(field_number << 3) | wire_type` as a varint. | A parser may encounter non-minimal varint encodings of the same tag if bytes are hand-crafted. | Emit only shortest-form tag varints. Reject or never construct non-minimal tag encodings. | Add low-level writer tests for multi-byte tag boundaries and no API for raw tag bytes. |
| Field order | Generated Java serialization emits known fields in generated field-number order for normal messages. | Protobuf parsers accept fields in any order. Serialization order is explicitly not canonical across implementations. | Emit fields in ascending schema order unless a repeated/map schema says otherwise. | Keep order in `CanonicalSchema`; constructor rejects non-ascending singular field declarations. |
| Unknown fields | Java generated messages can preserve and re-emit unknown fields when parsing arbitrary input. Bisq-built model objects do not need unknown fields in canonical preimages. | Unknown fields may be serialized before/after known fields depending on implementation and version. | Canonical serializers never emit unknown fields. | Do not serialize from parsed protobuf messages directly; serialize from Bisq model objects only. |
| Duplicate singular scalar fields | Java generated builders store one value; when parsing duplicate singular fields, protobuf merge rules keep the last scalar value. | Wire bytes may contain the same singular field multiple times and parse to the same logical value. | Emit each singular scalar field at most once. | Add schema tests that each field number appears once unless field type is explicitly repeated/map. |
| Duplicate singular message fields | Java parsing merges duplicate message fields. Serialization of a built object emits the merged message once. | Multiple wire instances of the same message field can parse to one logical message. | Emit each singular message field at most once, with nested canonical bytes. | Treat duplicate message fields as non-canonical if a future reader/verifier is added. |
| `int32` | Java writes positive values as shortest varint; negative `int32` values are sign-extended and take 10 bytes. | Hand-crafted bytes can encode positive values with non-minimal varints. `sint32` would use zigzag, but `int32` does not. | Positive `int32`: shortest varint. Negative `int32`: standard 10-byte two's-complement varint. Omit default `0` unless schema says otherwise. | Keep separate writer methods for `int32` and `sint32` if `sint32` is ever introduced. |
| `int64` | Java writes `int64` as shortest varint for non-negative values; negative values take 10 bytes. | Hand-crafted bytes can use non-minimal positive varints. | Shortest varint for non-negative values. Negative values use the standard 10-byte two's-complement varint. Omit default `0` unless schema says otherwise. | Low-level tests should cover `0`, `1`, `127`, `128`, max positive, and `-1`. |
| `uint32` / `uint64` | Java writes shortest unsigned varints. | Non-minimal varints can parse to the same unsigned value. | Unsupported until a Bisq canonical schema needs it. If added, use shortest unsigned varints only and omit default `0` unless schema says otherwise. | Add unsigned writer methods before serializing a model field that uses unsigned protobuf integer semantics. |
| `sint32` / `sint64` | Not currently supported. Java would zigzag-encode then varint. | Confusing `int*` and `sint*` changes bytes for negative values. | Only add with explicit schema type and tests. Use shortest varint after zigzag. | Disallow until needed. |
| `bool` | Java writes `false` as default omitted and `true` as varint `1` when present. | Parsers commonly treat any nonzero varint as true. | If added, write only `0` or `1`; omit `false` unless explicit presence is required. | A future canonical reader should reject nonzero values other than `1`. |
| Enum | Java writes the numeric enum value. Java declaration order only matches protobuf order for some enums. | Proto3 can preserve unknown enum numeric values. Different languages expose unknown enum values differently. | Use schema-defined enum codes. Ordinal fallback is allowed only when documented and tested for that enum. Omit code `0` unless schema says otherwise. | Add one test per enum that locks every value in scope. |
| Fixed-width integers | Not currently supported. Java fixed32/fixed64 use little-endian fixed-width bytes. | No varint malleability, but signed/unsigned interpretation and endian mistakes are possible. | Disallow until needed; if added, schema must name fixed32/fixed64/sfixed32/sfixed64 explicitly. | Add byte-level fixtures for endian order. |
| Float / double | Not supported. | NaN has many bit patterns; `0.0` and `-0.0` can be semantically surprising. | Disallow floating point fields in canonical preimages unless a future schema defines exact bit-level canonicalization. | Prefer integer fixed-point representations. |
| String | Java serializes `String` to UTF-8 bytes and generated proto3 string fields require valid UTF-8. | Different sources can normalize Unicode text differently before it reaches protobuf. Invalid UTF-8 may exist in hand-crafted length-delimited bytes for non-string fields. | Serialize Java `String` UTF-8 bytes exactly; no Unicode normalization; omit empty singular strings unless schema says otherwise. | If user-controlled Unicode ever affects consensus, document whether normalization is intentionally absent. |
| Bytes | Java writes the exact byte array or `ByteString` bytes. | Bytes are opaque; equivalent higher-level encodings are application-specific. | Write bytes exactly as stored. Omit empty singular bytes unless schema says otherwise. | Do not reinterpret byte fields as nested messages unless schema defines a message field. |
| Length-delimited message | Java writes the nested message size as a varint and then nested bytes. | Non-minimal length varints may parse to the same length. Duplicate message fields may merge. | Length prefix is shortest-form varint. Nested bytes must be the nested schema's canonical serialization. | Add tests for zero-length nested messages only if a schema can actually emit one. |
| Repeated non-packed fields | Java emits each element in list order for non-packed repeated fields. | Repeated elements can be interleaved with other fields and still parse into the same list order in many cases. | Emit repeated fields at their schema position, preserving list order. Do not interleave with other fields. | Schema should mark the field repeated and define element order explicitly. |
| Repeated string/bytes | Java emits each element as a length-delimited field, including empty elements when present in the repeated list. | Empty repeated elements are distinct from absence but easy to accidentally drop in custom writers. | Preserve every element in list order, including zero-length elements. | Keep a parity test with an empty repeated string element. |
| Packed repeated numeric fields | Proto3 numeric repeated fields are often packed by default in generated code. | Parsers usually accept packed and unpacked encodings, and multiple packed segments may concatenate. | Unsupported until a Bisq canonical schema needs it. If added, the schema must explicitly choose packed or unpacked and reject alternatives as non-canonical. | Add before any packed repeated numeric field enters a canonical preimage. |
| Map fields | Java map serialization has implementation-specific ordering unless deterministic serialization is enabled; deterministic map ordering is still not guaranteed canonical across all languages for every key type. Some legacy Bisq paths also have explicit `HashMap` order hotspots. | Maps are repeated entry messages; wire order may vary, duplicate keys may parse with last value winning, and deterministic ordering can differ by language/key type. | Unsupported until a Bisq canonical schema needs it. If added, model maps as repeated entries with key field `1` and value field `2`; schema must declare `LegacyHashMapOrder`, sorted key order, or another explicit order mode. Duplicate keys are non-canonical. | Do not rely on protobuf deterministic map serialization. Implement and test the chosen order mode. |
| Oneof | Java builder stores one selected case and generated serialization emits that one field. | Wire bytes can contain multiple fields from the same oneof; parsers normally keep the last one seen. | Schema must emit exactly the selected oneof field for the Java object type, or none if allowed. Multiple oneof fields are non-canonical. | Add explicit unsupported-path tests for oneof alternatives outside the supported schema graph. |
| Default scalar values | Proto3 generated Java omits implicit-presence default scalar values. | Explicit `optional` fields and wrapper messages can preserve presence for defaults. | Omit default scalar values for implicit-presence proto3 fields. If presence is needed, schema must mark it explicitly. | Do not infer presence from Java default values. |
| Empty singular string/bytes | Java generated proto3 serialization omits empty singular strings and bytes. | Length-delimited zero-length fields can often parse to the same default value. | Omit empty singular string/bytes fields. | Use repeated fields if empty elements must be represented. |
| Groups | Deprecated and not supported. | Protobuf wire format supports start/end groups. | Disallow groups in canonical preimage schemas. | Fail schema construction if group support is ever requested. |
| Extensions / MessageSet / Any | Not supported. | These can carry arbitrary nested type URLs, extension fields, or unknown data. | Disallow unless a future schema defines a narrow canonical form. | Prefer explicit message fields. |

## Schema Implications

Each `CanonicalSchema` should define:

- message name;
- schema version;
- field number;
- protobuf-compatible field name;
- field type;
- field value accessor;
- omission rule;
- nested compose or extend schema, if any;
- repeated/map ordering rule;
- oneof selection rule, if any.

The encoder should execute the schema: serialization should iterate schema fields instead of maintaining a parallel manual writer. Human-readable docs can summarize the schema, but they must not become a second source of truth.
