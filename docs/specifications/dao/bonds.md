# Bonds

A **bond** is BSQ locked on-chain for a declared purpose, so that the DAO can destroy it
(*confiscate* it) if the bonded party misbehaves. A bond is the economic backing for two things:
[bonded roles](bonded-roles.md) and [bonded reputation](bonded-reputation.md).

The security property every bond consumer relies on is:

> While a bond is reported as active, the declared amount of BSQ is locked, is not spendable by its
> owner, and can be confiscated by DAO vote.

Everything in this specification exists to make that statement true, or — where it cannot be made
true — to make the violation visible instead of silent.

## 1. Lockup

A **lockup transaction** locks BSQ. It carries an `OP_RETURN` output with:

| Field | Meaning |
|---|---|
| op-return type | `LOCKUP` |
| version | lockup data version |
| lockup reason | parser-recognized `BONDED_ROLE`, `REPUTATION` or compatibility value `UNDEFINED` |
| lock time | number of blocks the collateral stays locked after unlocking starts |
| hash | 20-byte hash identifying *what* is bonded |

Rules:

- The op-return payload must be exactly 25 bytes and its reason byte must map to a recognized
  `LockupReason`; an unknown reason makes the output invalid.
- The lock time must be within `[1, 65535]`. `0` is rejected because the DAO does not support a
  lockup that becomes spendable in its own block.
- The locked value is the value of the lockup output, which is output index 0.
- The hash is opaque at this layer. Its meaning is defined by the lockup reason: for `BONDED_ROLE` it
  is the role hash, for `REPUTATION` it is a salted reputation hash.

`UNDEFINED` is a recognized compatibility enum value and the consensus parser currently accepts it
as a generic lockup reason. Such collateral follows the normal parser-level lockup and spend rules,
but it is neither a bonded role nor bonded reputation and grants no consumer-domain benefit.
Changing parser acceptance of `UNDEFINED` would be a consensus change and requires a separate
activation decision.

No Bisq client can create such a lockup, because the lockup transaction is always built with
`BONDED_ROLE` or `REPUTATION`; producing one requires a hand-built transaction. It is nevertheless
reachable, because the reason byte is only checked for mapping to a known enum value. The consequence
is a visibility gap: since neither consumer domain selects it, an `UNDEFINED` lockup appears in no
bond inventory, so its owner cannot see the locked BSQ and the DAO cannot name it in a confiscation
proposal. This is the same gap as [bonded-roles.md](bonded-roles.md) §7 and is recorded there. Whether
any such output exists in mainnet history is unverified.

**A lockup transaction is permissionless.** Anyone can publish one carrying any hash and any reason.
Binding a lockup to something that grants authority is therefore *never* the lockup's own claim to
make; it is a rule of the consuming domain. See [bonded-roles.md](bonded-roles.md) §3.

## 2. Unlock

An **unlock transaction** spends the lockup output. It is recognised as an unlock only if it has the
canonical shape:

- it spends exactly one lockup output, and
- its output at index 0 has exactly the same value as the lockup output, which must also equal the
  whole available BSQ input value.

The unlock output becomes spendable once `lockupSpendHeight + lockTime` blocks have passed. Unlocking
does not burn or move the BSQ; it starts the timer after which the owner can spend it normally.

### 2.1 Non-canonical spends

Bitcoin permits the owner to spend the lockup output with a transaction that does **not** have the
unlock shape. From the hard-fork-3 activation height, the BSQ parser classifies every such transaction
as invalid, burns all of its BSQ input value and admits none of its outputs as BSQ. The complete
consensus and activation rules are specified in [bond-lockup-spend.md](bond-lockup-spend.md).

The Bitcoin output and the DAO UTXO entry are nevertheless consumed. A bond ended this way must
therefore be reported as `ILLEGALLY_SPENT`, not left active. The state describes the lifecycle result;
it is not a workaround which preserves or follows the burnt collateral.

Below the activation height, historical parsing behaviour is preserved and a positive-valued
non-canonical spend can survive as an ordinary BSQ transfer. No such spend exists in the bundled
mainnet history through height 961400. The remaining pre-activation interval and other persistent
networks require their own audit; downstream state derivation must continue to fail closed for those
histories.

## 3. Bond state

| State | Meaning |
|---|---|
| `READY_FOR_LOCKUP` | The bonded asset exists and is eligible, but no valid lockup is known for it. |
| `LOCKUP_TX_PENDING` | An unconfirmed lockup transaction for this asset exists in the local wallet. |
| `LOCKUP_TX_CONFIRMED` | A confirmed, valid, unspent lockup backs the bond. **This is the only state that grants authority.** |
| `UNLOCK_TX_PENDING` | An unconfirmed unlock transaction exists in the local wallet. |
| `UNLOCK_TX_CONFIRMED` | An unlock transaction is confirmed. |
| `UNLOCKING` | Unlocked, lock time not yet expired. The collateral is still confiscatable. |
| `UNLOCKED` | Lock time expired. The collateral is free and no longer confiscatable. |
| `ILLEGALLY_SPENT` | The lockup output was spent by a non-canonical transaction (invalid and burnt after activation), or the spending transaction cannot be resolved at all. See §2.1. |
| `CONFISCATED` | The DAO confiscated the bond. |

`LOCKUP_TX_PENDING` and `UNLOCK_TX_PENDING` are **local wallet observations only**. They are never
observed by a node that does not own the wallet, and must never be used as an authorisation input.

### 3.1 State derivation rules

Given one lockup transaction and its output:

1. Start at `LOCKUP_TX_CONFIRMED` and record the lockup tx id, date, amount and lock time.
2. If the lockup output is spent:
   - by a canonical unlock transaction → `UNLOCKING` while the lock time has not expired,
     otherwise `UNLOCKED`;
   - by anything else, **or** if the spending transaction cannot be resolved → `ILLEGALLY_SPENT`.
     An unresolvable spender must never leave the bond confirmed; failing closed is required.
3. If the lockup output, or the unlock output derived from it, is confiscated → `CONFISCATED`.
   `CONFISCATED` overrides every state above.

Every lockup has its own lifecycle. The state of one lockup must never overwrite or mask the state of
another lockup carrying the same bonded-asset hash.

### 3.2 Active bonds

A bond counts as **active** in `LOCKUP_TX_CONFIRMED`, `UNLOCK_TX_PENDING`, `UNLOCK_TX_CONFIRMED`
and `UNLOCKING`. "Active" means *the collateral is still confiscatable*, which is why
`UNLOCKING` is included and `UNLOCKED`, `ILLEGALLY_SPENT` and `CONFISCATED` are not.

"Active" is deliberately weaker than "grants authority". Authorisation requires
`LOCKUP_TX_CONFIRMED` (see [bonded-roles.md](bonded-roles.md) §5).

## 4. Confiscation

The DAO can confiscate a bond by an accepted confiscation proposal naming the **lockup transaction
id**. Confiscation succeeds if:

- the lockup output is still unspent, or
- the lockup output was spent by a canonical unlock transaction whose unlock output is still unspent
  and still within the lock time.

Confiscation therefore cannot reach a bond whose lock time has already expired. It also has nothing
left to confiscate after a post-activation non-canonical spend because the parser has already burnt
the collateral. A subsequent accepted confiscation proposal is a no-op.

Confiscation is per lockup. It does not confiscate, invalidate or revoke another lockup carrying the
same bonded-asset hash, and it does not by itself permanently revoke the bonded asset.

**Requirement:** every lockup that could need confiscating must be reachable by the confiscation
proposal UI. See [bonded-roles.md](bonded-roles.md) §7 and
[bonded-reputation.md](bonded-reputation.md) §1.1 for currently unmet cases.

## 5. Freshness and fail-closed rebuilds

The DAO state can be rolled back and rebuilt (reorg, resync, snapshot restore). Bond collections are
caches derived from it.

**Requirements:**

- A bond collection must be fully rebuilt from the current DAO state on every update, not
  incrementally patched. Objects keyed by a client-supplied identifier must not survive a rebuild,
  because a bonded asset that is no longer accepted would otherwise keep its authority.
- If a rebuild fails part-way, the collection must be emptied rather than left holding the previous
  contents. A partially rebuilt collection must never be able to authorise anything.
- Any component that answers authorisation questions must re-check, at answer time, that the bonded
  asset is still accepted in the current DAO state.
- An external authorisation endpoint must reject requests until initial DAO parsing has completed and
  while DAO monitoring reports that the local state is conflicting or not connecting.

## 6. Rationale notes

- **Why the first input's public key is the ownership proof.** Bisq builds BSQ transactions with the
  BSQ inputs before any BTC fee inputs, so input 0 is always an input of the transaction creator's
  BSQ wallet. This is the established convention across the DAO code (compensation issuance ownership
  is recorded the same way). A transaction whose first input has no extractable public key cannot
  prove ownership and must fail closed rather than fall back to another key.
- **Why the lock time and amount are checked against approved terms rather than trusted.** Both are
  attacker-chosen fields of a permissionless transaction. See [bonded-roles.md](bonded-roles.md) §3.
