# Messages that a connection receives

## Scope

This specification covers how a connection reads each message that it receives, the size checks
that it applies before it passes the message to the handlers, and the limits that the senders of
large responses keep.

## Reading a message

- A message is preceded by its length as a varint, the format of the protobuf methods
  `writeDelimitedTo` and `parseDelimitedFrom`. Protobuf writes the length with at most 5 bytes. The
  connection reads the length prefix one byte at a time and decodes all its bits.
- If the length prefix has more than 5 bytes, if the length is larger than 10 MB
  (`Connection.MAX_PERMITTED_MESSAGE_SIZE`), or if the stream ends inside the length prefix, the
  connection does not read the message and closes at once for the rule violation
  `MAX_MSG_SIZE_EXCEEDED`, because the next message cannot be found in the stream.
- The connection reads all bytes of the message before it parses the message, in reads of at most
  64 KiB, and no byte of the next message. The buffer for the bytes grows while they arrive.
- All bytes of a message must arrive within 4 minutes after its length prefix. The connection checks
  the time after each read, also after the read which completes the message, and closes for
  `SOCKET_TIMEOUT` when the deadline has passed: at the latest one socket timeout of 240 seconds after
  the deadline.
- If the stream ends inside the message, the connection closes, also if the bytes received so far
  form a valid message.
- Bytes which are not a valid protobuf message violate `INVALID_DATA_TYPE`. The next message follows
  these bytes, so the connection reads on until the tolerance of the rule is reached. A failed parse
  does not keep the part of the message that was parsed.

## Size of the message type

- After the message is parsed, its size is checked against the limit of its type: 10 MB for the
  types with `ExtendedDataSizePermission` (data requests and responses, block and state hash
  responses, bundles of messages), 200 kB for all other types. An `AddPersistableNetworkPayloadMessage`
  whose payload has a hash of the wrong size violates the same rule.
- The size is the number of received envelope bytes, excluding the length prefix. Duplicate fields
  and noncanonical encodings count in full, even if protobuf normalizes them to a smaller message.
  Received-byte statistics use this same size.
- A message that violates one of these rules is not passed to the handlers. The tolerance of the
  rule only decides when the connection is closed: at the second violation of
  `MAX_MSG_SIZE_EXCEEDED`.

## Limits of the senders

A node keeps its responses below the limit of 10 MB:

- A `GetDataResponse` holds at most 60% of 10 MB of items with the lowest priority. The items of
  the other priorities do not count for this limit, see the known gaps in
  [`get-data-response-size-limit.md`](get-data-response-size-limit.md).
- A response with DAO, proposal or blind vote state hashes holds at most the 50000 hashes of the
  highest heights, about 1.5 MB ([`dao/seed-node-state-hashes.md`](../dao/seed-node-state-hashes.md)).
- A response with BSQ blocks or with Burning Man accounting blocks holds only the leading blocks
  whose serialized sizes add up to at most half of 10 MB, but at least one block
  ([`dao/lite-node-blocks.md`](../dao/lite-node-blocks.md)).

## Rationale

- `NetworkEnvelopeReader` is the receive part of `NetworkEnvelopeSocket` of Bisq 2, with the same code
  and the receive tests of `NetworkEnvelopeSocketTest`. The rules of this section are rules 3, 4 and 7
  to 10 of `docs/specifications/network/message-framing.md` of Bisq 2, with the size limit of Bisq 1.
- The connection read each message with `parseDelimitedFrom` and checked its size only after it was
  parsed. So any peer, also on an inbound connection whose peer is unknown, could make the node read
  and parse messages of up to 2 GB.
- `parseDelimitedFrom` keeps only the lower 32 bits of the length. A length of 2^32 would become 0, and
  the bytes of the message would be read as the next message. The decoder of the length prefix is not
  used for the bytes of the message, so it must not read ahead: it reads one byte at a time.
- The parsed objects of a message can need many times more memory than its bytes: 232 MB for a
  `GetDataResponse` of 10 MB with empty entries, 22 times its bytes, and 188 MB for a
  `PreliminaryGetDataRequest` of 10 MB with keys of 1 byte (protobuf-java 3.25.8, Java 21). A parser
  which reads from the stream creates the objects while the bytes arrive, so a peer which sends
  slowly would make the node hold them for that time. When the connection reads all bytes first, it
  holds only the bytes until the message is complete. The buffer grows with the bytes, so a length
  prefix alone does not make the node allocate the announced size.
- A parser which reads from the stream stops at the first invalid field and leaves the rest of the
  message in the stream, where it would be read as the next message. And protobuf has no end marker:
  a message which the stream cuts off at a field boundary is valid. Only the length tells where a
  message ends.
- The socket timeout of 240 seconds applies to each single read. A peer which sends one byte shortly
  before each timeout would otherwise keep the read thread and the connection without limit. The
  deadline of 4 minutes is the one of Bisq 2. The requesters of the large responses stop waiting at
  most 4 minutes after their request, so before the deadline of the response passes:
  `RequestDataHandler` and `RequestBlocksHandler` after 4 minutes, the requests of state hashes and
  of accounting blocks after 3 minutes. A read which waits cannot be interrupted without
  closing the socket from another thread, so the check after each read needs no further thread.
- To skip a message above 10 MB, the connection would have to read and discard all its bytes. Nodes
  of this version send no such message, and nodes of earlier versions only the responses of the
  compatibility section, so the connection closes instead.
- The first message above the limit of its type was passed to the handlers, because the rule
  tolerated one violation per connection. A stored entry above the limit then passed the next node
  in the same way.

## Known gaps

- Several connections can parse messages of 10 MB at the same time, each with up to 22 times its
  bytes as parsed objects. Any peer can send such a message, also as a valid request: a
  `PreliminaryGetDataRequest` of 10 MB with keys of 1 byte passes the checks of the connection and
  waits for its handling on the user thread. At up to 232 MB each, 18 such messages at the same time
  need more than the 4 GB heap of a seed node.

## Compatibility

Honest messages above 200 kB without `ExtendedDataSizePermission` are not known: dispute chat
attachments were removed in 1.10.0.

A node of an earlier version answers a request for state hashes or for accounting blocks with all of
them from the requested height on. From the DAO genesis height these are 12 MB of state hashes, which
the DAO monitor view requests on demand, and 17 MB of accounting blocks, which only a node without the
shipped accounting data requests. A node of this version closes the connection when it receives such
a response, so it gets these responses only from nodes of this version.
