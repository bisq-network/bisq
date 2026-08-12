# Merit

**Merit** is decaying vote weight earned by having had a compensation request accepted by the DAO. It
lets long-term contributors keep governance influence without holding a large BSQ balance.

A voter's weight on a proposal is `stake + merit`, where *stake* is the BSQ locked in the blind vote
transaction. Stake is on-chain and self-evident; merit is claimed inside the encrypted blind vote
payload and is therefore the part an attacker can lie about.

The security property every consumer of a vote result relies on is:

> The merit counted for a blind vote equals the decayed value of the compensation issuances that the
> voter actually received and still controls the key for, counted at most once each.

## 1. Merit claim

A blind vote carries a **merit list**. Each entry is a pair:

| Field | Meaning |
|---|---|
| issuance | A copy of the compensation `Issuance` the voter claims: `txId`, `chainHeight`, `amount`, `pubKey`, `issuanceType` |
| signature | ECDSA signature over the **blind vote transaction id**, made with the key of the first input of the compensation request transaction |

The merit list is encrypted and only revealed at the vote result phase, so it is not validated when
the blind vote is published. **Every field of a claim is untrusted until validated against DAO state**,
including the embedded issuance and its `pubKey`.

Signing the blind vote transaction id is what binds a claim to one specific blind vote: a claim
copied from another voter's revealed blind vote does not verify against a different blind vote id.

## 2. Validity of a merit claim

A claim contributes merit only when **all** of the following hold:

1. The claimed `issuanceType` is `COMPENSATION`.
2. An issuance with the claimed `txId` exists in DAO state **as a compensation issuance**.
3. The embedded issuance is **value-equal** to the DAO state issuance in every field.
4. The issuance block height is not greater than the blind vote transaction height.
5. The signature over the blind vote transaction id verifies against the **DAO state** issuance's
   `pubKey`.
6. No earlier accepted claim in the same evaluation already used that issuance (§4).

An invalid claim is skipped; it must not invalidate the other claims of the same merit list, and it
must not affect any other blind vote.

Rule 3 is what makes rule 5 meaningful. Verifying a signature against a key supplied by the same
party who supplied the signature proves nothing, so the verification key must come from DAO state.
Requiring full value equality — rather than only that the `txId` exists — additionally prevents a
claim from keeping a real `txId` while inflating `amount` or back-dating `chainHeight`.

Reimbursement issuances grant no merit. Only compensation requests do.

## 3. Decay and evaluation height

Merit decays linearly with the age of the issuance, from the full issuance amount at the issuance
block to zero at `2 × 50 000 = 100 000` blocks (about two years), and stays zero beyond that.

The age is measured against the **blind vote transaction height**, not the current chain height, so
the merit counted for a vote does not drift while the cycle progresses or when a result is
recalculated later during a resync.

Two different heights therefore appear, and they must not be confused:

| Height | Chooses |
|---|---|
| Blind vote transaction height | the decay applied to each issuance |
| Vote result evaluation height | which consensus rule version applies (§6) |

## 4. Uniqueness

**An accepted compensation issuance must back merit at most once per cycle.**

Without this rule a past contributor could restate the same issuance to obtain arbitrary vote
weight, either by repeating it inside one merit list or by publishing several blind votes in the same
cycle that each claim it.

Duplication is detected on the claimed issuance `txId`. When an issuance is claimed more than once,
the duplicate claims contribute nothing.

**Known gap:** the implementation currently enforces uniqueness only *within a single merit list*, so
one issuance cannot be counted twice by one blind vote. It does **not** enforce uniqueness *across the
blind votes of a cycle*: merit is derived per blind vote, so a voter who publishes several blind votes
in the same cycle has the same merit counted once per blind vote. The rule above is the intended
behaviour; §7 states what closing the gap requires.

## 5. Arithmetic safety

Summing merit must not silently wrap. An overflow in the merit total or in the decay computation is
a consensus fault: it must abort the vote result calculation rather than be reported as zero or as a
negative weight. Zero merit and "merit could not be computed" are different outcomes and must not be
conflated.

Rejecting an individual malformed claim is not a fault and must leave the remaining claims intact.

## 6. Activation and compatibility

The validation rules in §2 and the intra-list part of §4 were not enforced originally. They are a DAO
consensus change and activate at a fixed block height (`954 200` on mainnet, shared with the other
DAO consensus v2 rules).

Below the activation height the historical behaviour is preserved so that replaying old blocks
reproduces the persisted DAO state: a claim was accepted on the strength of its own embedded
`pubKey`, no DAO state lookup was made, and no uniqueness rule applied.

The rule version is selected by the **vote result evaluation height** of the cycle being evaluated,
not by the current chain tip. Consequently a cycle evaluated before activation keeps its historical
result forever, and any later change to the rules of §2–§4 must again be introduced at a new
activation height rather than by editing the active rule set.

## 7. Closing the remaining uniqueness gap

Because the activation height in §6 is already in the past, cycles have been evaluated under the
current rules. Extending uniqueness to the whole cycle therefore changes results that have already
been derived, and must not be applied by amending the active rule set in place. It requires:

- an audit of every cycle from the activation height onward for issuances claimed by more than one
  blind vote, and
- a new activation height, so that already evaluated cycles keep their result.

The check must be computed over **validated** claims only. Collecting claimed issuance ids before
validating them would let anyone publish a blind vote naming other voters' issuances with invalid
signatures and thereby suppress those voters' merit — turning a fix for vote inflation into a cheaper
vote suppression attack.

## 8. Display values are not consensus values

The merit shown in the UI, and the merit written to the vote result JSON export, is recomputed from
the persisted vote data. It is not the value that decided the result and must never be treated as
authoritative:

- Merit shown for one's own not-yet-confirmed blind vote is computed from the local wallet's own
  issuances at the current chain height, with no DAO state cross-check, because misreporting one's
  own merit harms nobody else.
- Merit displayed for a historical cycle selects the rule version by the *current* chain height, so a
  cycle evaluated before the activation height is displayed under the current rules and can differ
  from the value that was actually used.

Any display or export intended for cross-node comparison must state the evaluation height it used.

## 9. Rationale notes

- **Why the first input's public key.** Bisq builds BSQ transactions with the BSQ inputs before any
  BTC fee inputs, so input 0 belongs to the transaction creator's BSQ wallet. This is the same
  ownership convention used elsewhere in the DAO ([`bonds.md`](bonds.md) §6).
- **Why merit is bound to the blind vote id rather than to the voter.** The DAO has no identity
  layer. Binding the signature to the blind vote transaction id makes a claim non-transferable
  between blind votes without needing one.
- **Why validation happens at the vote result phase.** The merit list is encrypted until the vote
  reveal phase, which is what keeps votes blind. Nothing can be validated earlier, so the vote result
  phase is the only trust boundary available and must fail closed on every field it consumes.
