# Lite node block requests

## Scope

This specification covers how a lite node requests DAO blocks that it is missing from the seed
nodes. A lite node gets new blocks mainly from the block broadcasts of the full nodes. It requests
blocks from a seed node when its DAO state is behind:

- at startup, if the DAO state is behind the wallet,
- after it parsed a requested batch of blocks and is still behind the wallet,
- 10 seconds after the wallet saw a new block, if no broadcast brought that block,
- after a reorg, when the node reverted its DAO state to the last snapshot.

The validation of the blocks in a response is not covered here.

## Invariant

At most one block request runs at a time. The state of earlier block requests, their retries,
their tried seed nodes and their block heights, never prevents a new block request.

A block that no broadcast brings and a reorg both need a block request: broadcasts only bring new
blocks. If no request can be sent, the node keeps every new block as pending and its DAO state
stays behind until restart.

## Rules

- A block request goes to a seed node to which the node has a confirmed connection. Without such a
  connection, or if sending fails or no answer comes within 4 minutes, the node retries after 10
  seconds with a random seed node.
- Within one block request the node asks each seed node at most once and schedules at most 12
  retries. A request without a confirmed seed node connection starts with a retry. When no seed
  node or retry is left, the node gives up the request.
- Each new block request starts with no tried seed nodes and no retries. The retries and the seed
  nodes of earlier requests, successful or not, do not count.
- A new block request replaces an earlier request that is still running. The node stops the
  waiting retry of the earlier request and ignores its late answer or failure. The new request
  starts after the current DAO state, so the earlier request is not needed anymore.
- After a reorg the node reverts its DAO state to the last snapshot and requests the blocks after
  it. This is a new request like any other, also when its block height is below the height of an
  earlier request. If the node has to revert to the same snapshot again, it removes its DAO data
  and needs a restart, which builds the DAO state from the resources.
- When all connections are lost and come back, or after standby, the node asks again for the
  blocks of the last block request, also if it was already answered. This continues that request
  with its retries and tried seed nodes and does not start a new one.
- A seed node that was disconnected because it is banned is not asked again until restart.

## Compatibility

No message and no persisted data change. The limits of a single request are the same as before.

Earlier, the retries and the tried seed nodes were counted over the whole runtime, also for
successful requests, and normal operation could use them up. Then a block request reached a seed
node only over an existing seed node connection, and a block that no broadcast brought or a reorg
could leave the DAO state behind until restart. A new request could also run next to an earlier
one, and a late failure of the earlier request retried its old block height. If a retry of an
earlier request was waiting, a new request without a confirmed seed node connection was not sent,
and the retry asked for the old block height. A request below the last answered request was
dropped. This could drop the request for the blocks after a reorg.

## Not covered

- A response that the node rejects, because it is unsigned or has an invalid signature, does not
  start a retry with another seed node. This is a separate issue (#7971).
- If the asked seed node disconnects before it answers, the request ends without a retry. After
  the initial parsing, the next new block while the DAO state is behind starts a new request.
  During the initial parsing nothing does until all connections are lost and come back, standby,
  or a restart. This is older behaviour. Retrying with another seed node on such a disconnect is
  left for a separate change.
- The Burning Man accounting lite node uses the same retry pattern. It is left for a separate
  change.
