# Local Bitcoin node

## Scope

This specification covers the Bitcoin node that Bisq finds on localhost at the default port of the
Bitcoin network in use. If Bisq uses such a node, the wallet connects only to it and to no other
Bitcoin node. The use can be disabled with `--ignoreLocalBtcNode=true`, and a local node is never
used on the DAO regtest and DAO testnet networks. On `BTC_REGTEST` the node of the wallet is set
with `--bitcoinRegtestHost` instead: the check below still runs there, but it neither chooses the
peer nor asks the user.

## Rules

- Bisq uses a local node that is reachable on the port unless its answer shows that the wallet
  cannot use it as its only peer. The version message of the node must announce the full block chain
  (`NODE_NETWORK`), witness data (`NODE_WITNESS`) and bloom filters (`NODE_BLOOM`).
- A node without one of them is not used. A pruned node announces `NODE_NETWORK_LIMITED` instead of
  `NODE_NETWORK`, and Bitcoin Core announces bloom filters only with `peerbloomfilters=1`.
- Before the wallet connects to other Bitcoin nodes instead of a local node that it cannot use, the
  desktop asks the user, and the start of the wallet and of the P2P network waits for the answer.
  The popup names what the node lacks; the user can shut down Bisq to fix the node, or continue
  without it. Closing the popup shuts down Bisq. The daemon cannot ask, so it shuts down with the
  reason in its log; with `--ignoreLocalBtcNode=true` it starts with other Bitcoin nodes.
- If the node does not answer with a version message, for example because another service uses the
  port, the node is used without this check. Bisq waits at most 5 seconds for the whole answer, even
  when the answer arrives slowly.
- The check starts on its own thread when the application starts, and its result is kept until the
  application is restarted.

The wallet uses bitcoinj. It downloads blocks only from a peer that announces the full block chain
and witness data, and it disconnects a peer that does not support bloom filters. With a local node
that lacks one of them as its only peer, the wallet does not receive new blocks. Other Bitcoin nodes
learn from the bloom filters of the wallet which addresses belong to it, so a user who runs a local
node decides whether the wallet uses them.

## Compatibility

No message and no persisted data change.

A user whose local node is pruned or has bloom filters disabled is now asked at startup whether the
wallet continues with other Bitcoin nodes, as with `--ignoreLocalBtcNode=true`. Before, the wallet
did not synchronize while that node was running. A daemon with such a node now shuts down at
startup. The connection that detects the local node now also sends a version message and reads the
answer.

## Not covered

A node that is busy at startup and answers too late is used without the check. If it cannot serve
the wallet, the wallet does not synchronize until the next start. A change of the node configuration
while Bisq runs. Bitcoin nodes set in the preferences. The local node mode of the btcnodemonitor
uses the same check, although the monitor does not need bloom filters.
