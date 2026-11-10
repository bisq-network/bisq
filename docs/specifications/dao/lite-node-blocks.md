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
- With the option `skipBsqBlockProvidersSignatureVerification`, which is the default on regtest, a
  lite node does not verify the signature. It still requires the signed block.

## Rationale

Any peer can send a broadcast message. The signature binds the block to a trusted full node, so that
a peer cannot change the DAO state of lite nodes, for example BSQ balances or the receivers of trade
fees and of delayed payouts. Lite nodes of releases before 1.10.4 use the unsigned copy without any
check. If updated nodes relayed messages without a signed block, they would pass blocks of any peer
to these nodes.

## Compatibility

Lite nodes use the signed block from release 1.10.4 on. Lite nodes of releases 1.10.10 and 1.10.11
drop a broadcast message whose unsigned copy is missing or differs from the signed block, so full
nodes still send the unsigned copy. It can be left out once these releases are no longer supported.

Releases before 1.10.4 are not supported. They still read the unsigned copy of a full node's message
that other nodes relay; that copy matches the signed block.
