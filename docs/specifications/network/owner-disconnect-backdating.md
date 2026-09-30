# Owner disconnect backdating

## Scope

This specification covers storage entries whose payload is only valid while its owner is online.
Offers are the only such entries. The payload carries the address of its owner and a time to live
(TTL). The owner refreshes its entries before the TTL ends, and a node removes an entry whose TTL
has ended.

A node also shortens the remaining TTL of such an entry when the connection to its owner is lost
(backdating), so that the entries of an owner that went offline disappear sooner. This specification
defines which lost connections allow backdating.

A close of a connection is intended if the node shuts down, if the node closes the connection to
stay within its connection limits or because the address of the peer is unknown, or if the peer
requested the close. All other closes are unintended, for example after an error on the connection
or after a rule violation by the peer.

## Security invariant

A lost connection may shorten the TTL of an entry only if the node knows that the peer of that
connection is the owner of the entry.

The node knows the address of a peer only on an outbound connection: it is the address that the node
dialed, and a sender address that the peer claims must not replace it. On mainnet the node connects
only over Tor, and Tor authenticates the onion address. On an inbound connection the peer only
claims its address in its messages, and the node cannot verify it.

If a lost inbound connection allowed backdating, any peer could claim the address of another node
and close the connection. One or two such closes would bring the entries of that node to the end of
their TTL, and the node would remove them. A refresh from the owner does not restore a removed
entry, so a removed offer is missing until its owner publishes it again or the node requests data
from its peers again.

## Rules

- An unintended close of an outbound connection backdates each entry whose owner address is the
  address of the connection by half of its TTL. The entry is removed once its TTL has ended, unless
  the owner refreshes it first.
- An intended close does not backdate any entry.
- A close of an inbound connection does not backdate any entry, whatever address the peer claimed.

## Compatibility

No message and no persisted data change.

Backdating is not broadcast. It changes the creation time of the stored entry, and the node serves
the entry with that creation time to peers that request data from it. A node whose connection with
the owner was inbound now waits for the end of the TTL, and peers that request data from it receive
the unchanged creation time. This affects mostly nodes which accept many inbound connections, such
as seed nodes. The TTL of an offer is 9 minutes, so the offer of an owner that went offline can stay
up to 4.5 minutes longer than before.
