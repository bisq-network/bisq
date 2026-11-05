# Tor stream isolation

## Scope

This specification covers which connections of a Bisq 1 node may share a Tor circuit, and the option
`--torStreamIsolation`. It applies to the connections through the SOCKS port of the node's own Tor or of a running
Tor which the node uses ([`tor-startup.md`](tor-startup.md)).

## Terms

- **Circuit:** a path through Tor relays. Tor can carry several connections over one circuit.
- **SOCKS credentials:** the username and password of the SOCKS5 handshake. Tor does not check them. With the flag
  `IsolateSOCKSAuth` of its SOCKS port, which Tor turns on by default, Tor never puts two streams with different
  credentials on the same circuit. Streams with the same credentials may share a circuit.

## Required behaviour

### Peer connections

All outgoing connections to peers of the Bisq network send the same credentials, derived from the stream ID
`bisq-p2p`. They may share circuits with each other, but not with the other connections below. The option
`--torStreamIsolation` does not change them.

### HTTP requests

HTTP requests to price nodes, fee estimation, block explorers and other services go through the SOCKS port, unless
they go to the local host.

- Without `--torStreamIsolation`, the SOCKS client of the Java runtime connects. It sends the same credentials for all
  requests, so they may share circuits with each other and with the Bitcoin connections.
- With `--torStreamIsolation`, each HTTP request through the node's Tor sends new random credentials, so that Tor puts
  it on a circuit of its own. The node performs the SOCKS5 handshake itself, because the SOCKS client of the Java
  runtime cannot send credentials for each connection. It offers only username and password authentication and fails
  the request if the proxy does not accept it, so that a request never goes on without its credentials.

In both cases the node sends the target as a domain name, so that Tor resolves it, and HTTPS verifies the host name
of the target. A proxy which the user sets with `--socks5ProxyHttpAddress` or `--socks5ProxyBtcAddress` is always used
with the SOCKS client of the Java runtime, as without the option.

### SOCKS port

The isolation needs `IsolateSOCKSAuth` on the SOCKS port which the node uses. The node's own Tor gets its SOCKS port
from the node, and a torrc entry of the user cannot change it ([`tor-startup.md`](tor-startup.md)). The node refuses a
running Tor if an entry of its `SocksPort` configuration which may have opened the port that the node would use turns
`IsolateSOCKSAuth` off: the start fails with an error which names the entry and says to remove `NoIsolateSOCKSAuth`.

## Known gaps

- The Bitcoin connections through Tor use the SOCKS client of the Java runtime and drop the credentials of
  `--torStreamIsolation`, so they share the credentials of the HTTP requests without the option.
- The node checks the configuration which a running Tor reports on its control port. On Whonix, onion-grater does not
  let the query through, so the node logs that it could not check the SOCKS port of the Whonix-Gateway and uses it.

## Rationale

Explorer requests concern the transactions of the user's trades. If they shared a circuit, the operator of an exit
relay or of the explorer could link these trades to one user. Separate circuits for each request make that harder;
the content and the time of each request remain visible to the server. Without the option, Bisq keeps the earlier
behaviour, which builds fewer circuits, for example for the regular requests to the price nodes.
