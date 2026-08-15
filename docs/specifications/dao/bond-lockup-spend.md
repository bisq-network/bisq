# Spending of Bond Lockup Outputs

## Scope

This specification defines how the BSQ collateral locked by a bond lockup transaction may be spent, and how the DAO
state must treat a spend which does not follow the rule. It covers lockup, unlock, and confiscation as far as they
interact with spending, and the integrity requirements on parsed block data which the rule depends on. The governance
side of bonds (roles, reputation, confiscation proposals and voting) is out of scope.

## Background

A bond locks BSQ collateral so that the DAO can confiscate it while the bond is active. The lockup output is an
ordinary Bitcoin output controlled by the bond owner's key; Bitcoin itself does not restrict spending it. The lock
time is carried in the lockup transaction's OP_RETURN and is enforced only by the BSQ transaction parser as a DAO
consensus rule. The security of every bond therefore rests on the parser's spend rules, not on Bitcoin script.

## Rules

### Lockup

- A lockup transaction declares the lock time and lockup reason in its OP_RETURN. Its first output is the bond
  collateral (the lockup output).
- The parser-recognized `UNDEFINED` reason is retained for consensus compatibility. It receives the same generic
  lockup-spend protection but is not consumed as a bonded role or bonded reputation.
- A lockup output is not spendable by ordinary wallet coin selection.
- While the lockup output is unspent, an accepted confiscation proposal confiscates the bond.

### Unlock

- The only permitted spend of a lockup output is a formal unlock transaction: it spends exactly one lockup output,
  its first output carries exactly the value of that lockup output, it has no OP_RETURN and burns no BSQ. It may
  carry additional non-BSQ inputs and outputs, as the wallet pays the miner fee from BTC inputs.
- The unlock output is unspendable until the lock time (taken from the lockup transaction) has passed, counted from
  the unlock transaction's block.
- While the unlock output is unspent and the lock time has not passed, an accepted confiscation proposal still
  confiscates the bond.
- Spending an unlock output before its unlock height burns the spent value.

### Any other spend of a lockup output (from the hard fork 3 activation height)

- A transaction which spends a lockup output and is not a formal unlock transaction is invalid: all its BSQ input
  value is burnt and none of its outputs are BSQ.
- One rule covers every non-unlock shape, including: splitting the value over several outputs, adding further inputs
  so the exact value match fails, declaring a new lockup from the old collateral without unlocking, spending several
  lockup outputs in one transaction, a spend in the same block as the lockup, and a spend whose values happen to
  match the unlock pattern while its OP_RETURN declares something else.
- A spend of a zero valued lockup output must still be recorded as an invalid transaction, even though it carries no
  BSQ input value; the lockup output must not silently disappear from the UTXO set without a recorded end of the
  bond.
- The burnt BSQ recorded for an invalid transaction includes the full destroyed BSQ input value, including inputs
  which spent unlock outputs before their unlock height.
- A confiscation attempt which arrives after such a burn must be a no-op, not an error. The collateral is already
  destroyed, which is economically equivalent for the owner.

### Before the activation height

- Historical behaviour is preserved so that reparsing old blocks reproduces the persisted DAO state: below the
  activation height a non-unlock spend of a lockup output is parsed as an ordinary BSQ transfer, a zero valued
  lockup spend is not recorded, and the burnt BSQ of an invalid transaction omits prematurely spent unlock inputs.
- No non-unlock lockup spend exists in mainnet history up to the DAO state snapshot at chain height 962500. The
  bundled block history contains 187 lockups: 102 remain unspent and all 85 spent lockups were spent by formal unlock
  transactions. The interval after the previous audit, heights 961401 through 962500, contains no new lockup or unlock
  transaction.

## Block data integrity

The parser applies height gated consensus rules — including the activation check of this rule — to the block height
carried by the transaction itself, and it addresses the UTXO set by the transaction id and block height carried by
each output. Parsed block data must therefore be internally consistent:

- Every transaction in a block must carry the containing block's height and hash.
- Every output must carry its containing transaction's id and its containing block's height.

A block which violates this is rejected as a whole before any DAO state is mutated. The rejection is unconditional
(not height gated): every honest source of block data stamps these fields from the containing block and transaction,
so no historical block is affected. Without this, data from an untrusted block source could claim a pre-activation
height to evade height gated rules, or register an output under a foreign UTXO key and thereby replace a lockup
output entry with an ordinary spendable one.

## Invariant

From the activation height on, bond collateral can leave the locked state only through a formal unlock — which
applies the lock time and keeps the bond confiscatable until the lock time has passed — or by being burnt. Locked
collateral can never re-enter the spendable BSQ supply without the lock time having run.

## Rationale

Confiscation needs a full DAO cycle and names its target in plaintext from the proposal phase on, while a transaction
spending the lockup output confirms in one block. If a non-unlock spend recovered the collateral as ordinary BSQ, a
bond owner who did not cooperate always won the race against confiscation and did not even need to wait for a
proposal to appear; the honest exit was delayed by the lock time while the dishonest one was not. That inverts the
incentive the bond exists to create. Burning the collateral instead means a hand-built escape spend costs the owner
exactly what confiscation would.

## Activation and Compatibility

- The rule is a DAO consensus change (hard fork 3) and activates at a fixed block height per network, following the
  same scheme as earlier DAO hard forks. Nodes which have not upgraded diverge in DAO state hash from the activation
  height on if such a transaction occurs.
- The activation heights must be set from the release schedule to lie safely after the release which ships the rule.
