# Messages that a connection receives

## Scope

This specification covers how a connection reads each message that it receives, the size checks
that it applies before it passes the message to the handlers, and the limits that the senders of
large responses keep.

## Reading a message

- A message is preceded by its length as a varint, the format of the protobuf methods
  `writeDelimitedTo` and `parseDelimitedFrom`. Protobuf writes the length with at most 5 bytes. The
  connection reads the length prefix one byte at a time and decodes all its bits.
- If the length prefix has more than 5 bytes, or if the length is larger than 10 MB
  (`Connection.MAX_PERMITTED_MESSAGE_SIZE`), the connection does not read the message and closes at
  once for the rule violation `MAX_MSG_SIZE_EXCEEDED`, because the next message cannot be found in
  the stream.
- The connection reads all bytes of the message before it parses the message, in reads of at most
  64 KiB, and no byte of the next message. The buffer for the bytes grows while they arrive.
- All bytes of a message must arrive within 4 minutes after its length prefix. The connection checks
  the time after each read, also after the read which completes the message, and closes for
  `SOCKET_TIMEOUT` when the deadline has passed: at the latest one socket timeout of 240 seconds after
  the deadline.
- If the stream ends inside a length prefix of at most 5 bytes or inside the message, the connection
  closes with the reason `TERMINATED`, without a rule violation, also if the bytes received so far
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

## Bundles of messages

A `BundleOfEnvelopes` carries several messages, its elements. Since v1.8.0 only `Broadcaster`
bundles messages: it sends the broadcast messages of 2 seconds to a peer in one bundle. It sends them
earlier if the bundle with the next message would be larger than 10 MB, because a peer of this version
closes the connection at such a message (see below). Before, a relay which received more than 10 MB of
broadcast messages within 2 seconds put them all into one bundle. A node broadcasts the entries of high
priority of a getdata response again 60 seconds after it received them, also entries which it did not
store, so a response full of such entries led to such a bundle.

- An element of a known type which cannot be parsed violates `INVALID_DATA_TYPE` and is not passed
  to the handlers, as a single message which cannot be parsed. The other elements are passed to the
  handlers, unless a violation closes the connection: then no element of the bundle is passed to
  the handlers and the connection reads no further message.
  - Before, such an element was dropped without a trace, so a peer could send any number of
    elements which the node parses and resolves in vain, without a violation.
  - An element with a payload of a type which the node does not know, for example a stored entry
    of a newer version, or with such a type nested in its payload, for example a new type of
    proposal in a `TempProposalPayload`, counts in the same way. Before, it failed the conversion of
    the whole bundle and closed the connection (`UNKNOWN_EXCEPTION`), as a single message with such
    a payload still does.
  - An element whose conversion fails with another error, for example an offer without the ID of
    its fee transaction, still fails the conversion of the whole bundle: no element of the bundle is
    passed to the handlers and the connection closes, as for a single message with such an error.
- An element of a message type which the node does not know, as one of a newer version, is dropped
  without a violation. Protobuf keeps the field of such a type as an unknown field, so the element
  has no message type for the node.
  - A relay of a newer version passes a broadcast message of a new type on to nodes of this
    version, unless the type requires a capability which they do not have. Not all broadcast
    message types require one: `NewAccountingBlockBroadcastMessage` does not. The node cannot tell
    such an element from one without any message type, so with a violation for each, two bundles
    from an honest relay of a newer version would close the connection to it.
- Every new type in a broadcast message must reach only peers which know it: a new message type, a
  new payload type, and a new type nested in a payload, such as a new type of proposal in a
  `TempProposalPayload`. The sender tests the capabilities only of the message and of the payload of
  a storage message, so the message or the payload which contains the new type must require a
  capability for it.
  - A node sends a broadcast message alone, not in a bundle, when it has no other one for the peer
    within 2 seconds. A single message of a message type which the node does not know violates
    `INVALID_DATA_TYPE`, and a single message with a payload or a nested type which the node does
    not know closes the connection (`UNKNOWN_EXCEPTION`).
- Each element passes the checks of a single message before it is passed to the handlers: the size
  limit of its type, the hash size of the payload of an `AddPersistableNetworkPayloadMessage` and
  the message version of the network. An element which fails one of them is dropped without a rule
  violation. At most once per 10 seconds for each connection, the node logs such an element.
  - The size of an element is the size of the element serialized as the node handles and relays
    it. Bytes which the parse drops, such as unknown fields, count in the size of the bundle, which
    the rules above check against the received bytes.
  - Before, the elements skipped these checks: a bundle of 10 MB could deliver an element of almost
    10 MB, which the node stored and broadcast, without a rule violation.
  - Such an element causes no violation because an honest relay of an earlier version passes it on.
    Nodes of earlier versions do not check the elements of a bundle, and they store and relay
    payloads of any size, a `SignedWitness` which this version rejects as malformed (its check of
    the hash size also checks the keys of the witness since this version), and a
    `RefreshOfferMessage`, `NewBlockBroadcastMessage` or `NewAccountingBlockBroadcastMessage` with
    the message version which they received. The receiver cannot tell the relay from the peer which
    created the element. With a violation, any peer could send such elements to an honest relay of
    an earlier version and make the nodes of this version close their connections to it, which
    also removes the relay from their persisted peers (`bundle-element-wrong-network-closes-relay`,
    `bundle-wrong-network-relay-still-closable`).
- An element which is no broadcast message, or which is a bundle itself, violates
  `INVALID_DATA_TYPE` and is not passed to the handlers; the violation counts as for a single
  message. If the violation closes the connection, no element of the bundle is passed to the
  handlers and the connection reads no further message.
  - A message of another type in a bundle would skip the handling that only a single message gets:
    a `CloseConnectionMessage` closes the connection, a `SupportedCapabilitiesMessage` sets the
    capabilities, a sender address binds the connection. Nodes before v1.8.0 also bundled direct
    messages that they sent within 20 ms; a node of this version does not pass them to the handlers.
  - Since v1.8.0 nodes bundle only broadcast messages, so no relay passes such an element on.

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
- A bundle of broadcast messages, which is not a response, holds at most 10 MB (see Bundles of messages).
  The sender does not check the limit of the type of a message, and a message which alone exceeds 10 MB is
  sent on its own.

## Rationale

- `NetworkEnvelopeReader` is the receive part of `NetworkEnvelopeSocket` of Bisq 2, with the same code
  and the receive tests of `NetworkEnvelopeSocketTest`. The rules of this section are rules 3, 4 and 7
  to 10 of `docs/specifications/network/message-framing.md` of Bisq 2, with the size limit of Bisq 1.
  Bisq 2 fails with the same exception for an invalid length prefix and for an end of the stream
  inside it, and closes the connection in both cases without a penalty. The connection of Bisq 1
  closes for a rule violation at an invalid length prefix, so its reader reports an end of the stream
  inside the length prefix as an end of the stream.
- An end of the stream inside a length prefix is no rule violation, as an end inside a message is
  none: the peer closed the connection, which it can do at any time. A rule violation would also
  remove the peer from the persisted peers. A length prefix whose fifth byte announces a further byte
  has more than 5 bytes, so it stays a rule violation, also if the stream ends after it.
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
- A peer can send bundles with any number of elements without a message type, which the node parses
  and drops without a violation. Only the size limit of a bundle, 10 MB, bounds this work for each
  bundle.
- A peer which sends elements of bundles that fail the checks of a single message is not penalized
  for them. The node parses such elements with their bundle, serializes each again for the check of
  its size, and drops it. The parse is bounded by the size limit of a bundle and by the limits of
  messages per second and per 10 seconds, which count a bundle as one message; the parse memory
  budget (PR #276, `p2p-parse-memory-concurrent`) also bounds the memory of the parses at the same
  time. The allowance of the elements of bundles (PR #270, `p2p-bundle-throttle-granularity`) runs
  after the parse, so it does not bound the parse, but it limits the elements which are checked and
  serialized again to 1000 within 10 seconds for each connection.
- A relay of an earlier version still loses the connection when it passes such a message on alone,
  not in a bundle, which it does when it has no other broadcast message for the peer within 2
  seconds. A single message of another network violates `WRONG_NETWORK_ID`, which closes the
  connection at once, and a single message above the size limit of its type, or whose payload fails
  the check of the hash size, counts as a violation of `MAX_MSG_SIZE_EXCEEDED`, which closes it at
  the second. Either way `PeerManager` removes the relay from the persisted peers. This path
  predates the checks of the elements of bundles and is not changed by them.

## Compatibility

Honest messages above 200 kB without `ExtendedDataSizePermission` are not known: dispute chat
attachments were removed in 1.10.0.

A `NewBlockBroadcastMessage` or a `NewAccountingBlockBroadcastMessage` above 200 kB is now dropped
also when it arrives as an element of a bundle, which it does whenever its sender had other broadcast
messages within the same 2 seconds. Before, such an element was passed to the handlers. In a
bundle it is dropped without a rule violation; as a single message it counts as a violation of
`MAX_MSG_SIZE_EXCEEDED`, as before. A lite node which misses the broadcast of a block requests the
block from a seed node 10 seconds after its wallet saw the block.

A node of an earlier version answers a request for state hashes or for accounting blocks with all of
them from the requested height on. From the DAO genesis height these are 12 MB of state hashes, which
the DAO monitor view requests on demand, and 17 MB of accounting blocks, which only a node without the
shipped accounting data requests. A node of this version closes the connection when it receives such
a response, so it gets these responses only from nodes of this version.

Such a response closes the connection for the rule violation `MAX_MSG_SIZE_EXCEEDED`, and a node
removes the peer of a connection that closed for a rule violation from its persisted peers. Earlier
versions read the first message above 10 MB of a connection and closed the connection only at the
second one, so they kept the peer. A peer of an earlier version which sends such a response is now
removed from the persisted peers, although it is honest. Few peers are affected: the accounting
blocks and the regular state hashes come from seed nodes, which are normally not among the persisted
peers, as nodes do not report seed nodes to other nodes. The DAO monitor view sends its request for
the state hashes from the DAO genesis height to a peer that the user chooses, which can be such a
peer.

Seed nodes are to be updated before the other nodes. Nodes request the accounting blocks and the
state hashes mainly from seed nodes, and a node of this version gets the responses above only from
seed nodes of this version. A block response of an earlier version holds at most 3000 blocks, at most
about 8 MB with the blocks up to 1.10.12, so it stays below 10 MB.
