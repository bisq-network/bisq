# Signatures of received storage entries

## Scope

This specification covers the check of the owner signature of a protected storage entry that a node
receives: the add of an entry, the removal of an entry and the refresh of an offer. It defines when the
node verifies a signature and when it rejects an entry because the same signature failed its
verification before.

## Signed values

- The owner signs the hash of the payload and the sequence number. The verification uses the public
  key of the owner, this hash and the signature.
- The signature does not cover the creation date of the entry, and it does not cover the receiver key
  of a mailbox entry.

## Rules

- A node verifies the signature of an entry before it stores the entry, applies the removal or applies
  the refresh, and after the checks that need no signature, such as the check of the sequence number.
- A node remembers each signature that failed its verification, as the hash of the three values that
  the verification uses: the public key, the signed hash and the signature. A later entry with the same
  three values is rejected without a new verification, whichever connection it arrives on.
- Only a failed signature verification is remembered. A failure of a later check, for example of the
  owner of the payload or of the receiver key of a mailbox entry, is not remembered.
- Valid signatures are not remembered. A verification that cannot run, for example because the
  signature is not well formed, is not remembered either.
- A node remembers at most 10000 failed verifications. When it would remember more, it forgets the
  least recently used ones.
- The log line of a failed verification names the type and the size of the payload and the signed
  hash, not the content of the payload. The log lines of the checks of the owner, which follow a
  valid signature, of the check that a new entry has the key of the stored entry, and the line of a
  failed removal name the type and the size of the payload, its sequence number and hashes of the
  keys, not the content of the payload or the keys.

## Rationale

- The verification of a signature costs much more CPU than the checks before it, and it runs on the
  user thread, which also handles all other messages and the user interface. As the creation date is
  not signed, copies of one entry with an invalid signature that differ only in their creation date
  are different messages. Each copy cost one verification, and nothing was stored that would have
  stopped the next one.
- A verification gives the same result for the same public key, signed hash and signature. So the
  remembered result is the result that a new verification would give, and remembering it cannot
  reject an entry that a verification would accept.
- An invalid copy of a valid entry differs from it in at least one of the three values, because a copy
  with the same three values has a valid signature. So an invalid copy never makes the node reject the
  valid entry. A check after the signature depends on values that are not among the three, for example
  the receiver key of a mailbox entry, so its failure must not be remembered: a copy that differs only
  in such a value can be valid.
- About 150 bytes per remembered verification, 1.5 MB for the bound.
- A peer can make each invalid entry new at no cost, and a payload can have tens of kB. With the
  payload in the log line, one bundle of such entries wrote megabytes of log. An entry that fails the
  check of the owner costs the peer one signature with its own key, and a removal logged the whole
  entry after every failed check, also after an invalid signature, so these lines are bounded in the
  same way.

## Known gaps

- The rule only saves the verification of copies that repeat all three values, such as copies that
  differ only in their creation date. A peer needs no valid signature, so it can make each entry new
  at no cost: another well-formed signature alone is enough, as is another sequence number or
  another byte of the payload, and each such entry still costs one verification. So the rule stops
  naive replays, not a peer that sends invalid entries on purpose. The limits for this are
  elsewhere: the number of messages a connection may send (the throttle counts a bundle as one
  message, `p2p-bundle-throttle-granularity`) and the number of connections
  (`p2p-unbounded-inbound-connections`).
- The same peer can make the node forget the remembered failures: 10000 invalid entries with new
  values push them out, and the copies of a forgotten entry cost a verification again.
- A limit at the origin would be a budget of signature verifications per received message, for
  example per bundle of messages, beyond which the node drops the remaining entries of the message
  without a verification (`storage-signature-verification-budget`). It needs no rule violation, so
  it avoids the problem of the next point. Not covered: the budget and its effect on the bundles of
  honest peers need a design.
- A peer that sends entries with invalid signatures is not penalized. A rule violation would stop it
  at the origin, but an invalid signature does not prove a malicious peer: honest nodes relay only
  entries whose signature they verified, yet a node of another version can compute another signed
  hash for the same entry, for example when it does not know a field of the payload. With the
  tolerance of the existing rule violations a penalty could close connections to honest peers and to
  seed nodes. It needs a decision about the threshold and a compatibility review
  (`storage-invalid-signature-connection-penalty`).
- As the creation date is not signed, a valid entry can also be sent again with another date. That is
  a separate item (`storage-entry-replay-redate`).

## Compatibility

No message and no persisted data change. A node rejects the same entries as before; only repeated
invalid entries cost less.
