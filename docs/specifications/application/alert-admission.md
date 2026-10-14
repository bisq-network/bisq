# Alert admission

## Scope

This specification defines which alert messages a BISQ 1 node accepts from the P2P network, both
when an alert is received and when the alerts received during the bootstrap are processed at
startup, and which signatures a node adds when developers publish an alert.

## Rules

1. A node accepts an alert only if its alert signature verifies with the alert key the node trusts.
   The alert signature covers all fields of the alert except itself: the message, the version, both
   update flags, the message signature, the publisher's key and the extra data.
2. When developers publish an alert, the node signs the message with the alert key (the message
   signature) and then signs the alert as defined below (the alert signature). The alert signature
   is stored in the extra data of the alert under the key `alertSignature`.
3. An alert that announces a software update or a pre-release carries a version. A node accepts
   such an alert only if the version has the format `major.minor.patch` with decimal digits only,
   for example `1.10.10`. Otherwise the node ignores the alert. An alert without an update or
   pre-release announcement needs no version.
4. The window in which developers create an alert refuses an update or pre-release alert with such
   an invalid version.

## Signed format, version 1

The alert signature is a Bitcoin signed message, made with the alert key, over the lowercase
hexadecimal encoding of the following canonical statement:

| Field | Type | Value |
| --- | --- | --- |
| 1 | String | `bisq-alert-v1` |
| 2 | String | Message |
| 3 | String | Version |
| 4 | Boolean | Update flag |
| 5 | Boolean | Pre-release flag |
| 6 | String | Message signature |
| 7 | Bytes | Publisher's public key, as in the alert |
| 8 | Map of strings to strings | Extra data without the `alertSignature` entry |

Fields use canonical protobuf-style tags in ascending field order: booleans use varints, strings use
UTF-8, and strings, bytes and map entries are length-delimited. Fields of the statement with an
empty string, a false boolean, empty bytes or an empty map are omitted. Each map entry always
contains the key at field 1 and the value at field 2, also if the value is empty, and the entries
are sorted by key in Java `String` order (UTF-16 code units). Fixed vectors are kept in the test
resources (`alert-signature-v1-all-fields.txt`, `alert-signature-v1-empty-fields.txt`). Changing
the signed bytes requires a new domain, not a redefinition of version 1.

## Reasons

- Rule 1: every field of an alert decides what a node shows, for example whether the alert
  announces an update and of which version. All of them come from the holder of the alert key.
  The publisher's key is covered, so only the publishing node can sign an entry for the alert, which
  is needed to publish or remove it in the P2P network.
- Rule 2: the message signature is still added, so that nodes which do not check the alert
  signature accept the alert. The domain in field 1 separates the alert signature from the message
  signature, which is made with the same key. The alert signature is kept in the extra data, so the
  message format does not change.
- Rule 3: a node compares the version of an update alert with its own version when it shows the
  alert, also at startup. A version in another format cannot be compared. Checking the
  format when the alert is accepted keeps such an alert away from the comparison, so the node never
  has to handle it later.
- Rule 4: the format is also checked where an alert is created, so that no node receives an alert
  with an invalid version.

## Compatibility

No protobuf change. The alert signature is sent and persisted in the existing extra data of the
alert, which every released version keeps when it relays or persists an alert. Nodes of earlier
versions accept alerts published with this version as before. Nodes with this version ignore alerts
without an alert signature, including alerts published with earlier versions. Developers therefore
publish alerts with this version or later, and publish an alert which is still needed again.

A developer alert persisted by an earlier version carries no alert signature. The alert sender can
still remove it from the same data directory, because the removal is signed with the publisher's
key. Removing all alerts reaches only this developer alert and alerts with an alert signature, so
other alerts published with earlier versions are removed with an earlier version.

## Not covered

Rules for alerts beyond the signatures and the version format.
