# Bonded reputation

**Bonded reputation** is BSQ locked to a self-chosen hash rather than to a DAO-approved asset. It has
no Bisq 1 authority of its own; its purpose is to let a user demonstrate that capital is locked, which
Bisq 2 converts into a reputation score that gates Bisq Easy trade limits.

Because the score is bought with an economic commitment, the property that matters is:

> BSQ which backs a reputation score is either still locked or has been irreversibly destroyed. The
> same BSQ must never be recovered and reused to back another identity without completing a canonical
> unlock and its lock time.

## 1. What counts as a bonded reputation

A lockup output is a bonded reputation if and only if its lockup transaction's op-return carries the
lockup reason `REPUTATION`.

**Rules:**

- The reason is the sole selector. In particular, bonded reputation must **not** be defined as "every
  lockup that is not currently claimed by a bonded role bond": under that rule a
  `BONDED_ROLE`-reason lockup that no role claims silently becomes reputation, and the set changes
  whenever role bond selection changes.
- The bonded asset is the 20-byte hash from the op-return. It is opaque to Bisq 1 — a salted hash
  chosen by the user and linked to a Bisq 2 profile off-chain.
- Because the two lockup reasons are disjoint, the bonded reputation collection is independent of the
  bonded roles collection and must not be derived from it.
- Bond state is derived exactly as in [bonds.md](bonds.md) §3.1, including `ILLEGALLY_SPENT`.

### 1.1 Known behaviour: de-duplication by hash

Several lockups may carry the same reputation hash. The bonded reputation collection is keyed by that
hash, so those lockups collapse into a single entry and which one survives is not deterministic.

This affects only the local collection view (the bonds table and the set of confiscatable bonds); the
Bisq 1 → Bisq 2 export (§2) keys by lockup transaction id and is unaffected. This is recorded as
existing behaviour, not as a requirement — a per-lockup representation would be more correct, and the
current behaviour hides all but one of those lockups from the confiscation input, contrary to the
inventory requirement in [bonds.md](bonds.md) §4.

## 2. Export to Bisq 2

The Bisq 1 → Bisq 2 bridge streams BSQ blocks. For each transaction it may emit a bonded reputation
entry:

- for a `LOCKUP` transaction: an entry carrying amount, reputation hash, lock time and lockup
  transaction id;
- for an `UNLOCK` transaction: the same entry additionally carrying the unlock transaction id, which
  Bisq 2 treats as the removal signal.

Only lockups with a lock time of at least the minimum export lock time are exported.

The bundled mainnet resources through height 963120 contain 187 lockups, of which 51 have reason
`BONDED_ROLE`; none of those 51 has a lock time of at least 50 000 blocks. The reason-selector change
therefore removes no previously export-eligible `BONDED_ROLE` lockup from that bundled history. This
does not establish the result for later blocks or other persistent networks.

From the hard-fork-3 activation height, a non-canonical spend produces no removal entry because it is
not an `UNLOCK`, but the parser burns the collateral and none of its outputs remain BSQ. A separate
bridge invalidation event is not required to prevent reputation-value recycling: retaining a score
against permanently destroyed capital cannot let the same capital back another profile. The local
bond lifecycle still reports the terminal state as `ILLEGALLY_SPENT`.

Before activation, a positive-valued non-canonical spend can preserve the BSQ without producing a
bridge removal. No such spend exists in bundled mainnet history through height 963120. Heights 963121
through 963349 immediately before mainnet activation and other persistent network histories must be
audited before assuming the export invariant holds there.

## 3. My bonded reputations

The local wallet additionally tracks the user's own reputation bonds from a locally persisted list of
salts, so that the user can see and unlock bonds they created. This is a wallet-local view; it is not
authoritative for anyone else and grants nothing.
