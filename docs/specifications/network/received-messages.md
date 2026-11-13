# Messages that a connection receives

## Scope

This specification covers the size checks that a connection applies to each message it receives
before it passes the message to the handlers.

## Message size

- After the message is parsed, its size is checked against the limit of its type: 10 MB for the
  types with `ExtendedDataSizePermission` (data requests and responses, block and state hash
  responses, bundles of messages), 200 kB for all other types. An `AddPersistableNetworkPayloadMessage`
  whose payload has a hash of the wrong size violates the same rule.
- A message that violates one of these rules is not passed to the handlers. The tolerance of the
  rule only decides when the connection is closed: at the second violation of
  `MAX_MSG_SIZE_EXCEEDED`.

## Rationale

The first message above the limit of its type was passed to the handlers, because the rule
tolerated one violation per connection. A stored entry above the limit then passed the next node in
the same way.

## Compatibility

Honest messages above 200 kB without `ExtendedDataSizePermission` are not known: dispute chat
attachments were removed in 1.10.0.
