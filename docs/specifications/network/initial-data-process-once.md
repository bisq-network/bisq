# Process-once payloads of the initial data

## Scope

At startup a node requests the data it lacks from seed nodes, and from other peers if no seed node
answers. Some append-only payloads are marked as process-once: account age witnesses, signed
witnesses and trade statistics. These sets are large and seldom differ between the responders, so
the node applies them from one response only. This specification defines which response that is.

## Rules

- The node keeps two guards per run: one for the seed-node-only payloads (account age witnesses and
  signed witnesses), one for the other process-once payloads (trade statistics).
- Seed-node-only payloads are applied only from the response of a seed node that the node requested
  on an outbound connection. Other responses do not provide them and leave their guard as it is.
- The first seed node response that carries at least one seed-node-only payload uses up the guard
  of the seed-node-only payloads. A seed node response without such a payload leaves the guard open.
- While a guard is used up, the node skips the payloads of that kind in a response, unless the
  response is marked as truncated.

## Rationale

Account age witnesses and signed witnesses older than one day are not accepted from a broadcast, so
the initial data of a seed node is the only way to get them. A seed node that has nothing new for the
node sends a response without them. If such a response used up the guard, the node skipped the
witnesses that another seed node sent afterwards, and missed them until the next start. An account
whose witness is missing shows as new or as not signed, with the trade limits of such an account.

A response without seed-node-only payloads costs no work for them, so leaving the guard open does
not bring back the repeated processing of large sets that the guard exists to avoid: a second
response with such payloads is still skipped.

## Compatibility

No message and no persisted data change. Only the node that receives the responses decides which
response it applies.

## Not covered

- The guard of the other process-once payloads is still used up by the first response of any peer,
  also by one without such payloads.
- The last response of a series of truncated responses is not marked as truncated. If an earlier
  response of the series used up the guard, the node skips the process-once payloads of the last
  response and gets them at the next start.
- The responder sets the truncated mark. A responder can so make the node apply process-once
  payloads from each of its responses. Limits for the number and the size of these payloads are a
  separate topic.
