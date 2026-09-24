# BSQ swap seller inputs

## Scope

This specification defines what the BTC buyer of a BSQ swap must verify about the BTC seller's
inputs before it signs the swap transaction. It applies whether the buyer is maker or taker.

The seller builds the swap transaction and sends it to the buyer, together with a description of
each of its inputs: the parent transaction, the output index, the value and the script type. The
buyer uses the described values to check that the seller pays its share of the miner fee and does
not take more change than it is due. How the fees are shared is described in
[`../../bsq-swap-fee-handling.md`](../../bsq-swap-fee-handling.md).

The seller's check of the buyer's inputs is outside this scope. The seller builds the transaction
itself from the buyer's input descriptions, so the buyer's inputs are bound to their descriptions
by construction.

## Security invariant

Before the buyer signs, each seller input of the transaction must spend exactly the output that the
matching description names: the same parent transaction ID and the same output index, in the order
of the descriptions. Every check that uses a seller input value or script type must use the output
that the input spends.

The buyer's own signature does not protect the buyer here. It commits to the outpoints of all
inputs, but not to the values of the seller's inputs. If a seller input could spend another output
than the described one, the seller could spend a smaller output of a parent transaction and describe
a larger output of the same transaction. The buyer would then accept seller change that is paid from
the miner fee, including the buyer's own fee share. The taker may choose a fee rate up to twice the
maker's local estimate, which makes the buyer's fee share larger when the buyer is the maker.

## Rules

- The parent transaction ID is computed from the described parent transaction and commits to its
  outputs. The value and script type of the described output are therefore those of the spent
  output once the input spends exactly the described outpoint.
- The transaction must have one seller input for each description, after the buyer's inputs.
- The transaction must not spend the same outpoint twice. Otherwise a description could be listed
  twice and its value counted twice.

## BSQ outputs as seller inputs

The seller must pay with BTC only. Before the buyer signs, no seller input may spend an output which
the buyer's DAO state holds as an unspent BSQ output. The buyer's DAO state must be ready and in
sync to evaluate this rule; otherwise the buyer rejects the request.

A seller input which spends a BSQ output changes how the DAO parses the swap transaction. Spending a
lockup output from the hard fork 3 height on (see
[`../dao/bond-lockup-spend.md`](../dao/bond-lockup-spend.md)), or an unlock output before its lock
time has passed, makes the whole transaction invalid for the DAO. All BSQ inputs of an invalid
transaction are burnt and none of its outputs becomes a BSQ output, so the buyer loses its BSQ
change.

The rule is evaluated on the output which the seller input spends, as required by the security
invariant above. A check of the described output alone could be bypassed by describing a BTC output
of the same parent transaction.

An output of a BSQ transaction which the DAO classifies as a BTC output, for example the BTC change
of a transaction which paid a trade fee in BSQ, is not a BSQ output. The seller may spend it.

## Seller signatures

Before the buyer signs, each seller input must be in the form in which the seller's wallet signs it,
and its signature must be valid for the value and the script of the spent output. The seller's
wallet signs only P2PK, P2PKH and P2WPKH outputs, always with SIGHASH_ALL, in this form:

- P2WPKH: an empty scriptSig and a witness with exactly the signature and the compressed public key.
- P2PKH: a scriptSig with exactly the signature and the public key as shortest pushes, and no
  witness.
- P2PK: a scriptSig with exactly the signature as shortest push, and no witness.

The public key must be compressed or uncompressed; the network does not relay a hybrid encoded key.

The buyer rejects every other form. The script check which the buyer uses verifies signatures only
for these output types, and even for them it does not check all rules of the network, for example
the number of witness items or the ANYONECANPAY flag of a witness signature. A seller input which
the network rejects or does not relay leaves the buyer with a transaction which does not confirm. If
the seller can correct such an input later, it also gives the seller the same option as an unsigned
input.

These SIGHASH_ALL signatures cover the outpoints of all inputs and all outputs, but not the scripts
and witnesses of the buyer's inputs, so they stay valid when the buyer signs its own inputs.

The rule prevents the buyer from signing a transaction which is incomplete because of the seller's
inputs. It does not remove the seller's option to decide later whether the swap happens: an input of
a parent transaction which the seller has not published has the same effect, and the buyer cannot
detect it (see Not covered).

## Compatibility

Honest sellers of all versions build their inputs from their own descriptions, so the binding rules
do not reject an honest transaction. Messages and persisted data do not change.

The seller's BTC wallet does not exclude BSQ outputs from its coin selection. A seller whose BTC
wallet holds a BSQ output, for example BSQ sent to one of its BTC addresses, can select it. The
buyer now rejects such a request instead of signing a transaction which changes how the DAO parses
the swap.

The buyer now also checks that its DAO state is ready and in sync when it processes the seller's
request.

## Not covered

- The buyer uses an SPV wallet and cannot verify that a seller input exists, is unspent and is
  confirmed. The seller can therefore delay or prevent the confirmation of the swap, for example
  with an input of a parent transaction which it has not published or which it replaces later. The
  buyer's wallet then shows a swap which does not confirm.
- An output which the buyer's DAO state does not hold yet, for example an output of a parent
  transaction which is not confirmed, is not detected as a BSQ output.
- The buyer commits the swap transaction to its wallet and sends it to the seller before the
  broadcast result is known, and a broadcast timeout counts as success. A transaction which the
  network rejects can therefore still complete the trade on the buyer side.
- The fee rate tolerance for the taker is not changed. With the invariant above the seller cannot
  take back any part of the miner fee, so a high fee rate costs the seller its own fee share too.
