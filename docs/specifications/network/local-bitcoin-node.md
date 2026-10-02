# Local Bitcoin node

## Scope

This specification covers the Bitcoin node that Bisq finds on localhost at the default port of the
Bitcoin network in use. If Bisq uses such a node, the wallet connects only to it and to no other
Bitcoin node. The use can be disabled with `--ignoreLocalBtcNode`, and a local node is never used
on the DAO regtest and DAO testnet networks. On `BTC_REGTEST` the node of the wallet is set with
`--bitcoinRegtestHost` instead, and this specification does not apply.

## Rules

- Bisq uses a local node only if it is reachable on the port and its version message announces
  what the wallet needs from its only peer: the full block chain (`NODE_NETWORK`), witness data
  (`NODE_WITNESS`) and bloom filters (`NODE_BLOOM`).
- A node without one of them is not used, and the wallet connects to the Bitcoin network as if no
  local node was running. The reason is logged. A pruned node announces `NODE_NETWORK_LIMITED`
  instead of `NODE_NETWORK`, and Bitcoin Core announces bloom filters only with
  `peerbloomfilters=1`.
- If the node does not answer with a version message, for example because another service uses
  the port, the node is used without this check.
- The check is done when the local node is first needed, and its result is kept until the
  application is restarted.

The wallet uses bitcoinj. It downloads blocks only from a peer that announces the full block chain
and witness data, and it disconnects a peer that does not support bloom filters. With a local node
that lacks one of them as its only peer, the wallet does not receive new blocks.

## Compatibility

No message and no persisted data change.

A user whose local node is pruned or has bloom filters disabled now uses the Bitcoin network, as
with `--ignoreLocalBtcNode`. Before, the wallet did not synchronize while that node was running.
These are requirements that the information popup about a detected local node already lists. The
connection that detects the local node now also sends a version message and reads the answer.

## Not covered

A change of the node configuration while Bisq runs. Bitcoin nodes set in the preferences.
