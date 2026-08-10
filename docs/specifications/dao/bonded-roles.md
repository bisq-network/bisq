# Bonded roles

A **bonded role** is a DAO-accepted role proposal associated with one or more independent
[bond](bonds.md) lockups. Bisq 1 role bonds are voluntary economic collateral. Bisq 2 role authority
is granted only by a registration signed with the accepted proposal key and bound to one exact
confirmed lockup.

## 1. Role and role proposal

A `Role` is `(uid, name, link, bondedRoleType)`. Its **role hash** is the hash of its canonical
encoding and is what a lockup transaction references.

A `RoleProposal` carries a `Role` plus `requiredBondUnit` and `unlockTime`. The required bond is
`requiredBondUnit × BONDED_ROLE_FACTOR`, with the factor read at the proposal transaction height.
Every proposal field is untrusted until the DAO accepts the proposal.

### 1.1 Open: proposal-carried bond terms vs. role-type constants

`BondedRoleType` also defines a required bond unit and unlock time. The normal client derives proposal
terms from those constants, but proposal validation does not reconcile arbitrary wire values with
them. The implementation treats the accepted proposal terms as authoritative. Changing proposal
validation is consensus-critical and requires its own activation decision.

## 2. Canonical accepted proposal

A role uid is client-generated and can be copied into another proposal.

For one uid:

- The uniquely oldest accepted proposal by proposal transaction block height is canonical.
- A later accepted proposal with the same uid cannot replace it.
- If several accepted proposals share the oldest height, the role is ambiguous and unusable. A txid
  tie-break is forbidden because txids are grindable.
- If any candidate proposal height cannot be resolved, the role is unusable.
- A proposal whose role content differs from the canonical role does not define that role.
- Rejected proposals grant no role authority.

`(name, bondedRoleType)` is not unique. Security-sensitive requests must therefore identify the
canonical proposal transaction, not rely on name and type alone.

## 3. Valid role lockup

A lockup is a **valid role lockup** only when:

1. Its transaction type is `LOCKUP`.
2. Its validated OP_RETURN reason is `BONDED_ROLE`.
3. Its OP_RETURN hash equals the canonical accepted role hash.
4. It is mined strictly after the proposal transaction block.
5. Its lock time is at least the proposal's `unlockTime`.
6. Its locked amount is at least the required bond computed from the proposal terms and factor at the
   proposal transaction height.

Non-positive terms or factor and multiplication overflow make every lockup invalid for that role.
Such an output remains visible for management when its hash matches an evaluated proposal, but it
cannot back a Bisq 2 registration.

Lockup publication is permissionless. Its input key is neither the role identity nor evidence that
the proposal owner approved that particular lockup.

## 4. Independent lockup lifecycles

Every role lockup has its own bond object and lifecycle. There is no selected, winning or canonical
lockup for a role, and multiple active lockups are not a conflict.

- Publishing, unlocking, illegally spending or confiscating one lockup never changes another
  lockup's state.
- A canonical unlock changes only its input lockup to `UNLOCKING`, then `UNLOCKED`.
- A non-canonical spend changes only its input lockup to `ILLEGALLY_SPENT`.
- Confiscation changes only the named lockup to `CONFISCATED`.
- A role may therefore have confirmed, unlocking, unlocked, illegally spent and confiscated rows at
  the same time.

If a role-level display summary is required, it may report that collateral exists while at least one
valid lockup is `LOCKUP_TX_CONFIRMED`. That summary must not replace or overwrite individual states.

Bisq 1 does not derive code-enforced role authority from this summary. A third party that locks BSQ
for an accepted Bisq 1 role obtains no role identity or privilege and exposes its own collateral to
confiscation.

That exposure depends on the lock time outlasting a confiscation vote. `BondedRoleType` declares 110
days for every current role type, while a mainnet DAO cycle is about 32.5 days, so unlocking in
response to a published confiscation proposal does not free the collateral before the vote completes.
A role proposal carrying a much shorter `unlockTime` would remove that property, because proposal
terms are authoritative and are not reconciled against the role-type constants (§1.1).

### 4.1 Scenario matrix

The following rules apply independently to each lockup. "Other lockups" means other lockups carrying
the same role hash; it does not imply that any of them is selected or canonical.

| Situation | Subject lockup | Other lockups for the role | Bisq 2 registration consequence |
|---|---|---|---|
| First or additional valid lockup confirms | Its own row is `LOCKUP_TX_CONFIRMED`. | Unchanged. Several confirmed lockups, including several mined in the same block, coexist without precedence or conflict. | It can back a registration only when the proposal owner signs a message naming this exact lockup. An existing registration bound to another lockup is unchanged. |
| Lockup is mined in or before the proposal block, has insufficient amount or lock time, or otherwise fails §3 | It is not a valid authorization bond; when its hash matches an evaluated proposal it remains visible in the management inventory with its actual lifecycle state. | Unchanged. | It cannot back a registration. |
| Local wallet publishes an unlock which is not yet confirmed | That node reports only this row as `UNLOCK_TX_PENDING`. The chain still contains an unspent lockup output. | Unchanged. | That node rejects a new verification because the row is no longer `LOCKUP_TX_CONFIRMED`; other nodes cannot observe the pending transaction and may continue accepting it until confirmation. |
| Canonical unlock confirms and its lock time has not expired | Only this row becomes `UNLOCKING`; its unlock output remains confiscatable. | Unchanged, including any confirmed row. | A registration bound to this lockup fails subsequent verification. Registrations bound to other confirmed lockups are unchanged. |
| Canonical unlock reaches expiry | Only this row becomes `UNLOCKED`; its collateral is spendable and no longer confiscatable. | Unchanged. | A registration bound to this lockup remains invalid. |
| Non-canonical spend, or a spent output whose spender cannot be resolved | Only this row becomes `ILLEGALLY_SPENT`. From hard-fork-3 activation a non-canonical spender is invalid and the collateral is burnt; historical parsing is covered by [bond-lockup-spend.md](bond-lockup-spend.md). | Unchanged. | A registration bound to this lockup fails; another confirmed lockup can be registered with its own signature. |
| DAO confiscates the unspent lockup or its still-locked unlock output | Only this row becomes `CONFISCATED`. | Unchanged; confiscation is not role-level revocation. | A registration bound to the confiscated lockup fails. Registrations bound to other confirmed lockups are unchanged. |
| DAO attempts confiscation after unlock expiry or after a post-activation illegal-spend burn | No spendable collateral remains, so confiscation is a no-op; the row remains `UNLOCKED` or `ILLEGALLY_SPENT`. | Unchanged. | No registration becomes valid or invalid beyond the subject lockup's already terminal state. |
| Third party funds a valid lockup | The lockup follows the same independent lifecycle and the funder controls whether to unlock it. | Unchanged. | The funder gains no authority. The proposal owner may intentionally bind a registration to it by signing the exact lockup id; that sponsored-collateral relationship is outside the protocol. |
| Lockup hash matches only a rejected role proposal | It appears only in the management inventory. | Accepted-role lockups are unchanged. | It cannot back a registration. |
| `BONDED_ROLE` hash matches no evaluated role proposal | It has no `Role` object and is absent from the current management inventory; this is the known §7 gap. | Unchanged. | It cannot back a registration. |

Bisq 1 has no code-enforced authorization consequence in any row. Its UI displays the independent
collateral lifecycles and permits actions only on the exact lockup to which the action applies.

Confiscation is an economic penalty against named collateral, not a permanent revocation of the
accepted proposal identity. The proposal owner may fund a new lockup and create a new bound Bisq 2
registration after confiscation. Permanently removing a sanctioned role holder would require a
separate DAO-authorized role-revocation mechanism; no such mechanism is defined here.

## 5. Bisq 2 registration authority

The public key of the first input of the canonical accepted proposal transaction is the sole Bisq 2
role identity. There is no fallback to a lockup-input key.

A registration request identifies:

- bonded-role registration protocol version `1`;
- `bondUserName` and `roleType`, for the claimed public role;
- the canonical `proposalTxId`;
- the exact `lockupTxId` backing this registration;
- the Bisq 2 `profileId`; and
- the proposal-key signature.

The signed message is the lowercase hexadecimal SHA-256 digest of a canonical byte sequence. Each
field is UTF-8 encoded and prefixed by its four-byte big-endian length, in this order:

1. `BISQ_BONDED_ROLE_REGISTRATION_V1`;
2. `proposalTxId`;
3. `lockupTxId`;
4. `profileId`.

The message deliberately carries no network or DAO-instance identifier. Both transaction ids are
resolved against the verifier's own DAO state, so a signature produced against another chain or
another DAO instance cannot verify: its transaction ids do not resolve there. The domain tag
separates this message from any other message signed with the same key.

The verifier must:

1. Require protocol version `1`; a missing protobuf scalar has version `0` and must be rejected
   explicitly rather than inferred from empty binding fields.
2. Resolve `proposalTxId` to the canonical accepted proposal.
3. Check that its role matches `bondUserName` and `roleType`.
4. Resolve `lockupTxId` to a valid lockup for that exact role.
5. Require that lockup to be `LOCKUP_TX_CONFIRMED` at verification time.
6. Resolve the proposal transaction's first-input public key.
7. Verify the signature over the canonical message.

An unrelated lockup cannot activate, deactivate or impersonate this registration. The proposal owner
may explicitly register against collateral funded by a third party; that sponsorship relationship is
outside the protocol.

Once the bound lockup starts a confirmed unlock, is confiscated or is otherwise spent, subsequent
verification must fail even if another lockup for the same role remains confirmed. The proposal owner
may create a new signed registration bound to another confirmed lockup.

Consumers must persist the proposal/lockup binding with the Bisq 2 registration and revalidate it at
registration renewal or when consuming relevant DAO state changes. A bridge-local in-memory map is
not authoritative and must not be the only copy of the binding.

The previous request format signed only `profileId` and cannot be safely inferred as a binding to one
of several lockups. Consumers must migrate to the bound registration format; the verifier provides no
unbound fallback. The former REST route must return a structured upgrade-required response rather
than silently invoking the new verifier or returning an unexplained not-found response.

This is a coordinated migration requirement, not a wire-compatible continuation of existing
registrations. Before a bound-only verifier becomes authority-critical, Bisq 2 must send protocol
version `1` and both transaction ids, and existing role holders must create signatures over the bound
message. A deployment may provide a separately isolated legacy service during rollout, but this
verifier must not add an unbounded dual-accept fallback because doing so would restore unbound
authorization indefinitely.

### 5.1 Open: cutoff for the previous unbound format

A cutoff is intended, below which the previous unbound request format stays acceptable so that
existing Bisq 2 registrations keep working until their holders re-sign. It is not implemented; the
verifier currently accepts protocol version `1` only. Two points must be settled before that code
lands:

- The cutoff must be expressible in on-chain terms. This model has no registration timestamp, so the
  only well-defined anchor is the **block height of the bound lockup transaction**: below the cutoff
  height the previous unbound format is accepted, at or above it protocol version `1` is required.
- The no-fallback rule above then becomes bounded rather than absolute, and this section must state
  that bound explicitly instead of leaving the two statements in conflict.

## 6. Client lifecycle and actions

- The roles view displays every valid lockup for an accepted role as an independent row.
- The general bond-management inventory also shows BONDED_ROLE lockups matching rejected proposals
  and invalid candidates, so collateral remains discoverable and confiscatable.
- Sign and verify actions are offered only for a confirmed valid lockup and always use the proposal
  transaction key. The entered profile id is transformed into the canonical bound message before
  signing or verification.
- Proposal ownership, not the absence of an existing lockup, controls whether the client offers the
  role-level action to create collateral. The action remains available when another party funded a
  lockup and after an earlier lockup starts unlocking, unlocks, is illegally spent or is confiscated.
  The client may temporarily suppress it while its own same-role lockup transaction is unconfirmed,
  to prevent accidental duplicate publication.
- Unlock is offered only when the local wallet owns that exact lockup output. Proposal ownership does
  not imply ownership of collateral supplied by another wallet.
- Each still-confiscatable lockup is independently selectable in a confiscation proposal.
- The daemon/CLI `getbondedroles` response contains lifecycle entries rather than unique roles. A
  role with several lockups therefore appears several times with the same role uid; each entry carries
  its exact lockup transaction id when present and its bond state. Consumers must not key that
  response by role name or assume one entry per role.

## 7. Inventory completeness

Every BONDED_ROLE lockup whose hash matches an evaluated role proposal must remain visible, including
rejected-proposal lockups, invalid candidates and multiple lockups for one role. These management
entries never grant authority by themselves.

**Known gap:** a BONDED_ROLE lockup whose hash matches no evaluated role proposal has no `Role` domain
object and remains absent from the management inventory. A generic bond representation would be
needed to display and confiscate it without treating it as an authorized role.

## 8. Freshness

Repositories must rebuild independent lockup objects from current DAO state and clear their previous
contents if a rebuild fails. Bridge and REST verification must reject requests until DAO parsing is
complete and monitoring reports the local DAO state ready and in sync.

Bisq 2 retention, renewal and removal of an already accepted registration are outside this repository.
They must not assume that one successful Bisq 1 verification remains valid after the bound lockup
changes state.
