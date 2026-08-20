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

An entire merit ciphertext which cannot be decrypted is handled at the blind-vote payload boundary,
not as an individual claim. From the dedicated merit-decryptability activation, the blind vote keeps
its ballots and on-chain stake but receives an empty merit list. The payload remains part of the list
matched against the vote-reveal majority commitment. If several P2P payloads name the same blind-vote
transaction, a decryptable merit payload is preferred deterministically after that exact list is
matched; see [`vote-result-validation.md`](vote-result-validation.md).

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

Duplication is detected on the claimed issuance `txId`, in two scopes:

- **within one merit list**, where a repeated claim is ignored and the issuance still counts once; and
- **across the blind votes of the cycle**, where an issuance claimed by more than one blind vote counts
  for **none** of them.

The two scopes differ deliberately. Repeating a claim inside one list is a malformed list and is
simply reduced to the claim it contains. Claiming the same issuance from several blind votes is an
attempt to obtain the merit more than once, and there is no basis for deciding which blind vote should
receive it: awarding it to one would let whoever holds a stolen issuance key take it, and any
tie-break on transaction ids is grindable because an attacker can regrind their transaction. Dropping
every copy leaves nothing to gain from duplicating.

The accepted consequence is that a voter whose issuance key is compromised loses that piece of merit
if the thief uses it, as does the thief. Their remaining merit, their stake, and their ballots are
unaffected.

**Only validated claims take part in the duplicate detection.** A claim enters the comparison only
after it has passed every rule of §2. Comparing claimed issuance ids before validating them would let
anyone publish one cheap blind vote naming other voters' issuances with invalid signatures, and
thereby erase the merit of every honest voter in the cycle — a cheaper and broader attack than the
duplication it would be trying to prevent. Making an issuance look duplicated must require the ability
to sign for it.

Merit reaches the result only through ballots, so the comparison covers the blind votes which cast at
least one ballot. A blind vote without ballots contributes no vote weight, so a claim hidden in one
could not add weight either.

## 5. Arithmetic safety

Summing merit must not silently wrap. An overflow in the merit total or in the decay computation is
a consensus fault: it must abort the vote result calculation rather than be reported as zero or as a
negative weight. Arithmetic failure must not be conflated with the explicitly defined empty-merit
outcome for an undecryptable merit ciphertext.

Rejecting an individual malformed claim is not a fault and must leave the remaining claims intact.

## 6. Activation and compatibility

None of these rules were enforced originally, and they were not all introduced at once. Each is a DAO
consensus change and applies from its own activation height:

| Rules | Activates at |
|---|---|
| Validation against DAO state (§2), uniqueness within one merit list (§4), checked arithmetic (§5) | `954 200` on mainnet, shared with the other DAO consensus v2 rules |
| Uniqueness across the blind votes of a cycle (§4) | hard fork 3 |
| Undecryptable merit fallback and deterministic same-transaction-ID payload selection (§2) | dedicated blind-vote merit-decryptability height |

Below the first activation height the historical behaviour is preserved so that replaying old blocks
reproduces the persisted DAO state: a claim was accepted on the strength of its own embedded
`pubKey`, no DAO state lookup was made, and no uniqueness rule applied.

The cycle-wide and merit-decryptability rules are separated from the earlier ones because the
`954 200` height had already passed when they were written: cycles had been evaluated under the v2
rules, and tightening those rules in place would have changed results which were already derived and
persisted. The merit-decryptability rule also has its own setting rather than being coupled to hard
fork 3, even where their initial heights are equal.

The rule version is selected by the **vote result evaluation height** of the cycle being evaluated,
not by the current chain tip. Consequently a cycle evaluated before an activation height keeps its
historical result forever, and any later change to the rules of §2–§5 must again be introduced at a
new activation height rather than by editing an active rule set.

## 7. Deployment obligations for the later rules

The cycle-wide uniqueness rule activates with hard fork 3 at the finalized heights in
`DaoHardFork`: `963 350` on mainnet, `3 000 000` on Bitcoin testnet, and `1` on regtest and the DAO
test networks. The blind-vote merit-decryptability rule initially uses those same network heights
through independent constants. These consensus constants must not be changed after deployment.

The separate constants are intentional even where their initial values match hard fork 3: the
failure boundary, majority-list ordering, and cycle-wide merit-claim rule are independent consensus
decisions. Release approval must name each rule and, for the merit-decryptability and equal-ID
ordering rule, verify the height used by both vote reveal and vote result.

Because the rule version is selected by the evaluation height of the cycle itself, activating it
cannot change a result which has already been derived, including when a node rebuilds its DAO state
from genesis. The audit below therefore does not establish compatibility — the height gate does. What
it establishes is whether the weakness was exploited while it was open.

### 7.1 Audit of the bundled mainnet DAO state

Audited over the shipped `DaoStateStore_BTC_MAINNET`, whose chain height is `963 120`. Claims were
validated with the rules of §2, with the blind vote height taken from the vote reveal transaction
which spent the blind vote stake output, which is in the same cycle but slightly later and therefore
applies the "not younger than the blind vote" rule slightly permissively. The result is thus an upper
bound on the set of counted claims, which is what an audit for the *absence* of duplicates needs.

| Measure | Value |
|---|---|
| Cycles, of which with votes | 84, 82 |
| Decrypted blind votes | 876 |
| Compensation issuances | 1 527 |
| Merit claims | 7 927 |
| Merit claims passing validation | 7 927 |
| Most blind votes in one cycle | 14 |
| Issuances claimed again in a later cycle | 708 |
| **Issuances claimed by several blind votes of one cycle** | **0** |
| Same, without validating the claims at all | 0 |

No issuance was ever claimed by more than one blind vote of the same cycle, so the weakness was not
exploited up to that height, and no honest voter happened to trigger it either. The last two rows
matter together: 708 issuances *are* claimed again in later cycles, which is legitimate and is what
the per-cycle grouping has to tolerate, so the zero is a real result rather than an artefact of a
grouping which never compares anything. That every one of the 7 927 claims also passes validation
says separately that no forged, mismatched or unknown-issuance claim exists in that history.

Merit-ciphertext decryptability is audited separately with the complete bundled block and blind-vote
stores. The reproducible command and its 935 revealed-payload result are specified in
[`vote-result-validation.md`](vote-result-validation.md#historical-compatibility-audit).

### 7.2 Remaining obligations

- **The bundled resource ends at `963 120`.** Later completed cycles are not covered. Before release,
  confirm that no newer result phase has completed; if one has, run the same audit against a synced
  node through that cycle. In particular, the bundled resource does not cover heights `963 121`
  through `963 349` immediately before mainnet activation.
- **Continue the audit until activation.** An activation height which lies safely after release also
  lies in the future at release time, so those cycles cannot be audited in advance. Monitor or repeat
  the audit after each result phase between release and activation.
- **Preserve the finalized activation heights.** If release timing cannot satisfy the coordinated
  rollout, stop the release and make a new explicit consensus decision; do not silently move an
  activation height in an ordinary release change.

A monitoring check which reports, per cycle, any issuance claimed by more than one blind vote is
cheap to compute from DAO state and would surface this class of attack directly.

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
