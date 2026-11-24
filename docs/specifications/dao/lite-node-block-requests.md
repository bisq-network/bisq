# Lite node block requests

## Scope

A lite node gets new BSQ blocks from the broadcasts of full nodes. When its DAO state is behind the
wallet, it requests the missing blocks from a seed node:

- at startup,
- after it parsed the blocks of a response and is still behind,
- after the startup parsing, when its DAO state is still behind 10 seconds after the wallet saw a new
  block,
- after a reorg, when it reverted its DAO state to the last snapshot.

This specification covers how such a request reaches a seed node and when it is retried. Which blocks
of a response the node uses is specified in [`lite-node-blocks.md`](lite-node-blocks.md).

## Invariant

At most one block request runs at a time. A new request starts with all seed nodes and all retries:
the retries, the asked seed nodes and the start heights of earlier requests do not count for it. A
trigger for the start height of the running request starts no new request.

## Rules

- A request goes to a seed node to which the node has a confirmed connection, if there is one.
  Otherwise, and after each failed attempt, the node retries after 10 seconds with a random seed node
  that the request has not asked yet. An attempt fails if sending fails, if the connection carrying
  the request closes before the answer, or if no answer comes within 4 minutes.
- A request asks each seed node at most once and schedules at most 12 retries. When no seed node or
  no retry is left, the node gives up the request. With fewer than 12 seed nodes, as on mainnet with
  6, the seed nodes run out first.
- A request for another start height replaces the running request. The node stops the waiting retry
  of the replaced request and ignores its late answer or failure. A request for the start height of
  the running request does not replace it.
- A request may start below the start height of an earlier request, as after a reorg.
- When the node has no connection at all, as at a start before its first connection, a request waits
  for a connection instead of asking a seed node. With the next connection the request continues
  with a retry after 10 seconds.
- When the node has a connection again after it lost all connections, and when it awakes from
  standby, it requests the blocks of the last request again if that request was not answered. This
  is a new request, which starts with a retry after 10 seconds.
- A seed node that was disconnected because it is banned is not asked again until restart.

## Rationale

Broadcasts bring only new blocks. A block that no broadcast brought, and the blocks after a reorg,
reach the node only through a request. A node that cannot request blocks keeps each new block as
pending, as it does not connect to its DAO state, and the DAO state stays behind until restart.

Many requests start with a retry, because connections to seed nodes do not last. Counted over the
whole runtime, the retries and the asked seed nodes run out in normal operation; mainnet has 6 seed
nodes. So they count per request.

With one request at a time, its retries and asked seed nodes are well defined, and a late failure of
an earlier request cannot start retries for its start height. A request for another start height asks
for the blocks after the current DAO state, so the answer of the replaced request is not needed. A
trigger for the same start height asks for the same blocks as the running request. The node keeps
that request with its attempt in flight, which may be answered soon, or with its waiting retry.

The seed node answers over the connection that carried the request, so after that connection closed
no answer can come. The node may have another connection to the same seed node. If that connection
closes, the attempt can still be answered. Counting it as failed would drop the answer, and if no
other seed node is left, the request would give up.

Without connections and during standby every attempt fails. These failures tell nothing about the
seed nodes. So a request waits while the node has no connection, which includes the start before
the first connection, and an unanswered request starts again with fresh retries after the node lost
all connections or was in standby. An answered request is not repeated, as a seed node would send
the same blocks again; after the startup parsing the wallet starts a new request for any block that
is still missing.

## Compatibility

No message and no persisted data change. The limits that applied to the whole runtime now apply to
each request.

## Known gaps

- A response that the node rejects, because it has unsigned blocks only or a block with an invalid
  signature, counts as answered and ends the request without a retry
  ([issue 7971](https://github.com/bisq-network/bisq/issues/7971)).
- After the startup parsing, the next new wallet block starts a new request when an earlier request
  ended without the blocks. During the startup parsing only a reorg or a restart does, and for an
  unanswered request also a new connection after all connections were lost or a standby.
- The Burning Man accounting lite node keeps the earlier behaviour: it counts its retries and asked
  seed nodes over the whole runtime, ends a request without a retry when the asked seed node
  disconnects, and drops a request below the start height of the last answered request.
