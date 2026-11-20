# BSQ blocks for lite nodes

## Scope

A lite DAO node does not parse the Bitcoin chain. It gets the BSQ blocks from full DAO nodes: new
blocks as a broadcast message, missing blocks as the response to a request. This specification
defines which of these blocks a lite node uses and relays.

## Rules

- A full node signs each block that it sends with the signature key of its node.
- A lite node uses a block only if the block is signed and the signature verifies against a trusted
  block provider. The trusted block providers are the node addresses and public keys of the release,
  or those given with the option `bsqBlockProviders`.
- A broadcast message for a new block carries the signed block and an unsigned copy of the same
  block. A lite node uses and relays the message only if the signed block is present, its signature
  is valid and the unsigned copy matches it. The node relays the message unchanged.
- A lite node neither uses nor relays a broadcast message without a signed block.
- A lite node uses and relays the broadcast message of a block only once. It ignores a broadcast
  message of a block which is 1,024 or more blocks behind its local DAO tip. Historical sync obtains
  those blocks through the request and response protocol.
- To recognize a repeated broadcast message, a lite node remembers the blocks of the accepted
  messages until they are 1,024 or more blocks behind its local DAO tip. So it remembers every
  message which it would still accept. A reset clears that history.
- A full node answers each request for blocks with signed blocks only, whatever capabilities the
  requester announces. A lite node ignores a response that contains unsigned blocks but no signed
  block.
- With the option `skipBsqBlockProvidersSignatureVerification`, which is the default on regtest, a
  lite node does not verify the signature. It still requires the signed block.

## Response size

A full node answers a request for blocks with at most 3000 blocks from the requested height on, and
only with the leading blocks whose serialized sizes add up to at most half of the maximum size of a
message of 10 MB, but with at least one block. The lite node requests the blocks after the last one
it received until it reaches the chain tip. A Burning Man accounting node answers a request for
accounting blocks within the same size limit.

## Rationale

Any peer can send a broadcast message. The signature binds the block to a trusted full node, so that
a peer cannot change the DAO state of lite nodes, for example BSQ balances or the receivers of trade
fees and of delayed payouts. Lite nodes of releases before 1.10.4 use the unsigned copy without any
check. If updated nodes relayed messages without a signed block, they would pass blocks of any peer
to these nodes.

A signed block stays valid, so any peer can send the broadcast message of an old block again. Without
the height limit and the history of accepted messages every node would use and relay it again. The
history covers the same heights as the limit. With a history of a fixed number of messages a peer
could push a message out of the history which the node still accepts, and the message would circulate
again. The history grows only with blocks that a trusted block provider signed. A node at the tip
remembers about 1,024 blocks. A node which is behind also remembers the blocks above its tip whose
messages it accepted.

## Compatibility

Lite nodes use the signed block from release 1.10.4 on. Lite nodes of all these releases, including
1.10.12, drop a broadcast message whose unsigned copy is missing or differs from the signed block, so
full nodes still send the unsigned copy. To leave it out, lite nodes must first use and relay a
message without the copy, and the releases that require it must be no longer supported.

Full nodes of releases 1.10.4 to 1.10.11 send signed blocks only to a requester that announces the
capability `SIGNED_DAO_BLOCK`, so lite nodes still announce it until no full node runs these
releases.

Releases before 1.10.4 are not supported. Their requests get signed blocks, which they cannot read.
They still read the unsigned copy of a full node's broadcast message that other nodes relay; that
copy matches the signed block.

The largest 3000 BSQ blocks shipped with 1.10.12 have about 8 MB with signatures, so a lite node gets
them in two responses now. Lite nodes and accounting lite nodes of all releases request the blocks
after the last one they received, so a response with fewer blocks takes only one more request. A
node of an earlier version sends all accounting blocks from the requested height on, 17 MB from the
genesis height.
