# Seed node state hashes

## Scope

This specification covers the exchange of state hashes for DAO state monitoring: DAO state hashes,
proposal state hashes and blind vote state hashes. A node broadcasts the hash that it creates for a
new block to its peers. After it has parsed the blockchain at startup, it requests the recent
hashes from the seed nodes it is connected to. It compares the hashes of its peers with its own.

The hashes of seed nodes have two effects that the hashes of other peers do not have:

- If a DAO state hash of a seed node conflicts with the hash of the node, the node marks a conflict
  with the seed nodes, and the mark stays until the node restarts. While it is set,
  `DaoFacade.isDaoStateReadyAndInSync()` is false, and the node cannot start a trade as maker or
  as taker.
- Unless the full mode DAO monitor is enabled, the node does not create hashes for the blocks it
  parses at startup. It takes the missing hashes from the first seed node response, continues the
  hash chain with its own hashes, and stores the chain in its DAO state snapshot.

## Security invariant

A state hash counts as the hash of a seed node only if the node knows that the seed node sent it.

The node knows the address of a peer only on an outbound connection: it is the address that the node
dialed. On an inbound connection the peer only claims its address in its messages, and the node
cannot verify it.

If a hash on an inbound connection counted as the hash of the seed node whose address the peer
claims, any peer could mark a conflict with the seed nodes and block new trades of another node. If
the node sent its startup request over that inbound connection, the peer could also answer it.
Unless the full mode DAO monitor is enabled, its hashes would then become part of the hash chain and
the snapshot of the node, and the conflict with the real seed nodes would come back after every
restart until a DAO resync.

## Rules

- The node sends a state hash request only over an outbound connection. It dials the requested
  address if it has no outbound connection to it, and it never uses an inbound connection whose peer
  claims that address.
- The node accepts a state hash response only on an outbound connection to the requested address.
  It ignores a response on any other connection, and such a response does not end the request.
- The node ignores a state hash broadcast on a connection that it did not dial if the peer claims a
  seed node address.
- The node uses one seed node list both to decide whether a hash counts as the hash of a seed node
  and to decide whether it ignores a broadcast.

`RequestDataHandler` also sends its requests only over outbound connections, and it trusts seed node
data only on an outbound connection to the seed node.

## Compatibility

No message and no persisted data change.

If a seed node dialed the node, the node ignores the state hash broadcasts on that inbound
connection. It still requests hashes from that seed node over its own outbound connection.
If the node has no outbound connection to the requested address, the request now waits until the
node has dialed it. This also applies to a state hash request from the DAO monitor view to another
peer.

The conflict mark still stays until restart. With these rules only a seed node on an outbound
connection can set it, and a DAO resync remains the recovery. A node that already stored hashes
from an unverified peer keeps the conflict until a DAO resync.

The DAO monitor view still shows the hashes of other peers under the address that they claim. This
affects only the display.
