# Taker signature of the deposit transaction

## Scope

This specification covers which transaction the taker signs as the deposit transaction of the Bisq
v1 trade protocol. BSQ swaps are out of scope.

## The prepared and the signed transaction

The maker creates the deposit transaction from its reserved funds and from the raw inputs of the
taker, and sends it to the taker without its signatures. This is the prepared deposit transaction.
The taker does not sign the bytes it received. It builds a transaction from the raw inputs of both
traders and the outputs of the prepared transaction, and signs its own inputs of that transaction.

The delayed payout transaction spends output `0` of the deposit transaction, which it names by the
transaction ID. The buyer verifies and signs the delayed payout transaction against the prepared
deposit transaction. So the delayed payout transaction can only spend the published deposit if the
transaction that the traders sign has the transaction ID of the prepared one.

The transaction ID covers the version and the lock time of the transaction, the outpoint and the
sequence number of every input, and the outputs. The signature of a P2WPKH input covers the same
fields, so it is valid for one transaction ID only.

## Rules

- The taker signs its inputs only if the transaction it built has the transaction ID of the
  maker's prepared deposit transaction. Otherwise it creates no signature and the trade fails at
  this step. The rule applies to the buyer as taker and to the seller as taker.

## Rationale

The taker creates all inputs of the transaction it signs with the final sequence number
`0xffffffff`. Version, lock time, input outpoints and outputs of the prepared transaction are
checked before, but for the sequence numbers both `0xfffffffe` and `0xffffffff` are accepted, the
two values that do not signal replaceability. With `0xfffffffe` at one input of the prepared
transaction, the taker signed a transaction with another transaction ID.

A buyer as taker verified and signed the delayed payout transaction for the prepared transaction,
while its deposit signatures were valid for the other one. A seller with a modified client could
complete and publish that other transaction. The buyer's deposit was then locked in a multisig
output that no delayed payout transaction spends: no payout after the lock time, no refund agent
case, and every payout depends on the seller. A seller as taker creates the delayed payout
transaction from the transaction it signed, so it was not exposed, but the rule is the same for
both roles.

The rule removes the cause: the taker signs the prepared transaction or nothing, whatever field
differs.

## Compatibility

No message and no persisted data change. An honest maker of any version creates all inputs of the
deposit transaction with the final sequence number, so the transaction that the taker builds has
the transaction ID of the prepared one, and trades with makers of earlier versions are not
affected. Up to v1.10.11 the taker signed a transaction with another transaction ID if a sequence
number of the prepared deposit transaction was `0xfffffffe`.

## Not covered

- The buyer as taker verifies, signs and finalizes the delayed payout transaction against the
  prepared deposit transaction and not against the transaction it signed. With the rule both have
  the same transaction ID and the same outputs.
- A prepared deposit transaction with the sequence number `0xfffffffe` at an input passes the checks
  that the taker runs before it publishes its taker fee transaction. The taker refuses it only when
  it signs, so it pays the taker fee for a trade that fails.
