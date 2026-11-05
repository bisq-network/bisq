# Banned accounts

## Scope

This specification defines when an account shows as banned: in the badge of the peer of an offer
or a trade, in the details of a trade and its contract, in the warning when an offer is taken, and
in the views of the own payment accounts.
Accounts of payment methods without chargeback risk show no signing state, so they show no ban
either. The deny list of the release and the filter ban signature keys with
`bannedAccountWitnessSignerPubKeys`.

## The key of the owner decides

An account is banned if the signature key of its owner is banned. The ban is a property of the
owner, not of the account age witness, so it is decided by a key which the node knows to be the
owner's:

- for an offer, the signature key of the key ring of the offer. The P2P store accepts an offer only
  if this key signed it;
- for a trade, the signature key of the trading peer, from which the hash of the account age
  witness of the peer is computed;
- for an own account, the signature key of the node.

The owner key of a `SignedWitness` must not decide the ban. No signature covers it (see
[signed-witness-admission.md](signed-witness-admission.md)), and every fiat offer publishes the hash
of the account age witness of its maker. Anyone can therefore publish a witness for that hash with
a banned key as owner. The P2P store checks only the form of such a witness, and the date of a new
witness from the network. If the owner key decided, the account would show as banned on every node,
and the witness is never removed from the append-only store. Signing by an arbitrator before commit
`09141eba92` also put the key of the counterparty into the owner key, so the owner key of such a
witness names another trader.

The ban does not depend on the witnesses. A banned owner shows as banned also if no signed witness
exists for the account and if the node does not know the account age witness. A trade shows as
unsigned as long as the key of the peer is not known.

A banned account is never shown as signed, also if a witness of its account counts. The list of
offers shows it as not signed. The badge of the peer and the view of an own account show the ban
without a time since signing, and the details of a trade and its contract show the ban instead of
it.

## What the ban does not change

The banned state is shown, it does not set a limit ([trade-limits.md](../../trade-limits.md)). Where
a limit depends on peer signing, only the witnesses of a valid trust chain count (see the chain
rules in
[signed-witness-reputation.md](signed-witness-reputation.md#qualifying-signed-witness-chain)), and a
witness with a banned owner key is not part of a valid chain. This affects only that witness, so a
witness with a banned owner key which anyone publishes for another account cannot lower the limits
of that account.

## Compatibility

The banned state is computed locally. No message, stored data or network rule changes. Nodes of
older versions decide the ban by the owner keys of the witnesses and show an account with such a
witness as banned until they are updated.

## Not covered

- A banned owner can create a new key. The ban applies to a key.
- The other states of an offer (signed by an arbitrator, signed by a peer) come from the hash of the
  account age witness which the maker puts into the offer, and nothing binds this hash to the key of
  the maker. A trade does not use it: it computes the witness of the peer from the payment account
  data and the key of the peer.
- A witness with a banned owner key stays in the append-only store. It no longer changes the
  banned state.
