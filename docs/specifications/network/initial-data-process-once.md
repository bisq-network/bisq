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
- The first response of any peer uses up the guard of the other process-once payloads, also a
  response without them.
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

- A response can arrive after the node started its services, which it does when its wallet is ready
  and the initial data is complete, usually with the first response that is not marked as truncated.
  Such a response can be a further truncated response of a responder that the node asks again, the
  response to the request for updated data, which the node sends when its onion service is
  published, or a response after the node lost all its connections or woke up from standby. The node
  stores the process-once payloads that it applies from such a response without notifying the
  services that use them, and these services read the stored payloads only when they start. So these
  payloads take effect only at the next start, and the node drops a later broadcast of such a payload
  as a duplicate; a witness older than one day is not accepted from a broadcast anyway. This applies
  to the payloads of a truncated response, which the node applies also while the guard is used up,
  and to the witnesses that a later seed node response applies under the rules above. Before these
  rules, the node skipped such witnesses for the whole run.
- The guard of the other process-once payloads is still used up by the first response of any peer,
  also by one without such payloads (`initial-request-flag-overset-trade-statistics`, not changed).
  The node skips the trade statistics that only a later response carries, and misses them until the
  next start, unless a broadcast brings them. Leaving this guard open like the one of the witnesses
  would apply them as the previous item describes (`initial-data-late-trade-stats-not-observed`): the
  running node would lack these trades and their prices until the next start, and it would drop a
  later broadcast of such a trade statistic as a duplicate. A broadcast of a trade statistic has no
  age limit, so skipping them keeps such a broadcast effective.
- A single seed-node-only payload uses up their guard (`initial-request-guard-single-payload`): a
  seed node that has only one witness for the node keeps the node from applying the witnesses that a
  later seed node response carries.
- A seed-node-only payload counts for the guard when the node hands it to its storage, also if the
  node already has it or does not store it because it fails the check of its hash size
  (`initial-request-guard-counts-unstored`).
- The last response of a series of truncated responses is not marked as truncated. If an earlier
  response of the series used up the guard, the node skips the process-once payloads of the last
  response and gets them at the next start.
- The responder sets the truncated mark. A responder can so make the node apply process-once
  payloads from each of its responses. Limits for the number and the size of these payloads are a
  separate topic.
