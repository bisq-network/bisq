# Bonded roles

A **bonded role** is a DAO-accepted role proposal associated with one or more independent
[bond](bonds.md) lockups. Bisq 1 role bonds are voluntary economic collateral. Bisq 2 role authority
is normally granted by a registration signed with the accepted proposal key and bound to one exact
confirmed lockup. A height-bounded compatibility rule preserves the previous lockup-key registration
format for mainnet lockups mined before height `941000` (§5.1).

## 1. Role and role proposal

A `Role` is `(uid, name, link, bondedRoleType)`. Its **role hash** is the hash of its canonical
encoding and is what a lockup transaction references.

A `RoleProposal` carries a `Role` plus `requiredBondUnit` and `unlockTime`. The required bond is
`requiredBondUnit × BONDED_ROLE_FACTOR`, with the factor read at the proposal transaction height.
Every proposal field is untrusted until the DAO accepts the proposal.

### 1.1 Proposal bond-term validation

`BondedRoleType` defines the required bond unit and unlock time for each role type. From the
hard-fork-3 activation height, a role proposal is valid only when both proposal-carried values exactly
equal the constants of its declared role type. A mismatch excludes the proposal from consensus
ballot selection and proposal-state hashing even when its transaction and payload commitment are
otherwise valid. The proposal transaction block height determines activation; an unconfirmed
proposal is checked at the current chain height and checked again at its eventual transaction height.

Below the activation height, historical validation is preserved: proposal-carried terms remain valid
without comparison to the role-type constants, and accepted historical proposals continue to use
their carried terms. From activation onward, `requiredBondUnit` and `unlockTimeInBlocks` in an
existing `BondedRoleType` are consensus constants and must not be changed. Changing a constant or
adding a role type requires its own compatibility and activation decision.

### 1.2 Bond-term lifecycle and audited history

The three inputs to a role bond have different sources and mutability:

| Value | Source | Selection time | Effect of a later change |
|---|---|---|---|
| `requiredBondUnit` | Code-defined `BondedRoleType`, copied into the proposal | Proposal creation and, from hard-fork-3, validated at its transaction height | None. The proposal-carried value remains fixed. |
| `unlockTime` | Code-defined `BondedRoleType`, converted to network-specific blocks and copied into the proposal | Proposal creation and, from hard-fork-3, validated at its transaction height | None. The proposal-carried value remains fixed. |
| `BONDED_ROLE_FACTOR` | DAO parameter history; the enum contains only its default and validation metadata | Value effective at the proposal transaction height | A DAO parameter change affects only proposals mined from its activation height. Existing proposals and bonds retain their earlier factor. |

Thus `BONDED_ROLE_FACTOR` provides prospective global adjustment of the absolute collateral for new
role proposals. It does not resize collateral requirements for an already accepted proposal. Such a
retroactive change would need an explicit migration or replacement mechanism; evaluating the current
factor against old lockups could invalidate collateral immediately and is not the specified model.
There is no DAO parameter for role-specific units or unlock time.

Repository history and the bundled mainnet data were audited when the hard-fork-3 validation was
introduced:

- The `BONDED_ROLE_FACTOR` code default was introduced as `1000` on March 21, 2019 and has not changed
  in source history. Mainnet governance changed it once to `500.00`, effective at height `679387`.
  One proposal for that value was accepted and another was rejected.
- Numeric terms for every current role type were finalized before mainnet DAO genesis at height
  `571747` on April 15, 2019: the last unit change was March 24 and the common unlock time changed from
  75 to 110 days on April 7. No current role type's numeric terms changed after genesis.
- A temporary `ANALYTICS_OPERATOR` type was added with unit 1, changed to 2 and removed in September
  2019. It is absent from the bundled role-proposal data and does not alter any current role type.
- At bundled DAO-state height `961400`, all 60 evaluated role proposals (59 accepted and one rejected),
  all 60 append-only proposal payloads and the one temporary role proposal exactly match the current
  mainnet role-type units and 15840-block unlock time. Of the evaluated proposals, 38 were mined before
  the factor change and 22 from its activation height onward.

This audit establishes compatibility only through the bundled height. Source-defined enum constants
remain a consensus risk: changing an existing unit or unlock time after hard-fork-3 would make nodes
running different versions disagree about proposal validity. Changing the factor's enum default would
also rewrite historical parameter lookup before its first on-chain change. Existing constants and
defaults must therefore remain immutable. New role terms require a versioned role type and coordinated
activation; they must not be introduced by editing an existing enum entry.

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
From hard-fork-3 activation, a proposal cannot shorten that period because its carried bond terms must
match the role-type constants (§1.1). A historical pre-activation proposal retains its accepted
carried terms.

### 4.1 Scenario matrix

The following rules apply independently to each lockup. "Other lockups" means other lockups carrying
the same role hash; it does not imply that any of them is selected or canonical.

The registration consequences below describe protocol version `1`. A version `0` legacy registration
instead remains valid while at least one confirmed, valid pre-cutoff lockup for the claimed role has
a first-input key that verifies its `profileId` signature (§5.1).

| Situation | Subject lockup | Other lockups for the role | Bisq 2 registration consequence |
|---|---|---|---|
| First or additional valid lockup confirms | Its own row is `LOCKUP_TX_CONFIRMED`. | Unchanged. Several confirmed lockups, including several mined in the same block, coexist without precedence or conflict. | It can back a version `1` registration only when the proposal owner signs a message naming this exact lockup. An existing registration bound to another lockup is unchanged. |
| Lockup is mined in or before the proposal block, has insufficient amount or lock time, or otherwise fails §3 | It is not a valid authorization bond; when its hash matches an evaluated proposal it remains visible in the management inventory with its actual lifecycle state. | Unchanged. | It cannot back a registration. |
| Local wallet publishes an unlock which is not yet confirmed | That node reports only this row as `UNLOCK_TX_PENDING`. The chain still contains an unspent lockup output. | Unchanged. | That node rejects a new verification because the row is no longer `LOCKUP_TX_CONFIRMED`; other nodes cannot observe the pending transaction and may continue accepting it until confirmation. |
| Canonical unlock confirms and its lock time has not expired | Only this row becomes `UNLOCKING`; its unlock output remains confiscatable. | Unchanged, including any confirmed row. | A registration bound to this lockup fails subsequent verification. Registrations bound to other confirmed lockups are unchanged. |
| Canonical unlock reaches expiry | Only this row becomes `UNLOCKED`; its collateral is spendable and no longer confiscatable. | Unchanged. | A registration bound to this lockup remains invalid. |
| Non-canonical spend, or a spent output whose spender cannot be resolved | Only this row becomes `ILLEGALLY_SPENT`. From hard-fork-3 activation a non-canonical spender is invalid and the collateral is burnt; historical parsing is covered by [bond-lockup-spend.md](bond-lockup-spend.md). | Unchanged. | A registration bound to this lockup fails; another confirmed lockup can be registered with its own signature. |
| DAO confiscates the unspent lockup or its still-locked unlock output | Only this row becomes `CONFISCATED`. | Unchanged; confiscation is not role-level revocation. | A registration bound to the confiscated lockup fails. Registrations bound to other confirmed lockups are unchanged. |
| DAO attempts confiscation after unlock expiry or after a post-activation illegal-spend burn | No spendable collateral remains, so confiscation is a no-op; the row remains `UNLOCKED` or `ILLEGALLY_SPENT`. | Unchanged. | No registration becomes valid or invalid beyond the subject lockup's already terminal state. |
| Third party funds a valid lockup | The lockup follows the same independent lifecycle and the funder controls whether to unlock it. | Unchanged. | For a lockup at or above the cutoff, the funder gains no authority. The proposal owner may intentionally bind a version `1` registration to it by signing the exact lockup id. A pre-cutoff funder's key can verify only a legacy registration which the Bisq 2 oracle nodes already accepted before rollout (§5.1). |
| Lockup hash matches only a rejected role proposal | It appears only in the management inventory. | Accepted-role lockups are unchanged. | It cannot back a registration. |
| `BONDED_ROLE` hash matches no evaluated role proposal | It has no `Role` object and is absent from the current management inventory; this is the known §7 gap. | Unchanged. | It cannot back a registration. |

Bisq 1 has no code-enforced authorization consequence in any row. Its UI displays the independent
collateral lifecycles and permits actions only on the exact lockup to which the action applies.

### 4.2 Role-level revocation boundary

Confiscation is an economic penalty against named collateral, not a permanent revocation of the
accepted proposal identity. The proposal remains accepted after every lockup associated with it is
confiscated. The proposal owner may fund a new valid lockup and create a new bound Bisq 2 registration;
that registration grants authority again while its exact lockup remains confirmed and unspent.

Bisq 2 has an independent mechanism by which its security manager can ban a role without changing the
Bisq 1 proposal or bond registration. That consumer-level ban is sufficient to disable a sanctioned
role in the deployed Bisq 2 authority model, so the absence of DAO-level role revocation does not
currently require a Bisq 1 consensus change. The governance difference is explicit: a Bisq 2 ban is
controlled by the security manager, whereas a Bisq 1 role revocation would be controlled by DAO vote.

If DAO-controlled permanent revocation becomes a requirement, it should be introduced as a separate
proposal type targeting the exact canonical role `proposalTxId`. Acceptance should add that identity
to an append-only revoked set and make every existing and future registration for it invalid,
independently of its lockups. Existing collateral should retain its normal lifecycle and require a
separate confiscation decision. A replacement holder should require a new accepted role proposal
with a new uid and proposal identity. Confiscation itself must not imply role revocation because
anyone may fund a role lockup.

## 5. Bisq 2 registration authority

For protocol version `1`, the public key of the first input of the canonical accepted proposal
transaction is the sole Bisq 2 role identity. A lockup-input key is accepted only by the explicitly
bounded version `0` compatibility rule in §5.1.

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

For a version `1` request, the verifier must:

1. Require protocol version `1`; unsupported versions must be rejected explicitly rather than
   inferred from their transaction-binding fields. Version `0` is handled only by §5.1.
2. Resolve `proposalTxId` to the canonical accepted proposal.
3. Check that its role matches `bondUserName` and `roleType`.
4. Resolve `lockupTxId` to a valid lockup for that exact role.
5. Require that lockup to be `LOCKUP_TX_CONFIRMED` at verification time.
6. Resolve the proposal transaction's first-input public key.
7. Verify the signature over the canonical message.

An unrelated post-cutoff lockup cannot activate, deactivate or impersonate this registration. The
proposal owner may explicitly register against collateral funded by a third party; that sponsorship
relationship is outside the protocol.

Once the bound lockup starts a confirmed unlock, is confiscated or is otherwise spent, subsequent
verification must fail even if another lockup for the same role remains confirmed. The proposal owner
may create a new signed registration bound to another confirmed lockup.

Consumers must persist the proposal/lockup binding with the Bisq 2 registration and revalidate it at
registration renewal or when consuming relevant DAO state changes. A bridge-local in-memory map is
not authoritative and must not be the only copy of the binding.

After rollout, Bisq 2 oracle nodes must use version `1` for every new registration. The previous
request format remains available only to revalidate registrations which those oracle nodes had
already accepted; it must never authorize a lockup mined at or above the cutoff.

### 5.1 Legacy compatibility cutoff

Protocol version `0` is the previous unbound format. It carries `bondUserName`, `roleType`,
`profileId`, and a lockup-input-key signature over `profileId`; it carries no proposal or lockup
transaction binding. A missing protobuf version scalar is interpreted as version `0`. The legacy REST
route maps to the same version rather than requiring existing callers to change their URL.

The verifier accepts a version `0` request only if it can find at least one bond which:

1. belongs to the canonical accepted role matching `bondUserName` and `roleType`;
2. is a valid role lockup under §3;
3. is currently `LOCKUP_TX_CONFIRMED`;
4. was mined on mainnet strictly below height `941000`; and
5. has a first-input public key which verifies the signature over `profileId`.

Legacy verification is disabled on testnet, regtest, and DAO test networks because the historical
no-conflict assertion applies only to the audited mainnet lockup set.

A lockup at the cutoff height or later requires protocol version `1`. Because §3 requires every valid
lockup to be mined strictly after its canonical proposal transaction, a pre-cutoff legacy lockup also
implies a pre-cutoff proposal; a second proposal-height gate would be redundant.

The Bisq 2 oracle nodes, not this stateless verifier, control creation of bonded-role registrations.
They retain whether a registration existed before rollout and use version `0` only when revalidating
one of those existing registrations. They use version `1` for every newly submitted registration.
Consequently, a successful version `0` verification cannot create a new registration unless the
trusted oracle nodes violate this protocol rule.

Height `941000` is deliberately historical: the role lockups below it have been checked to contain no
conflicting lockups. The cutoff must not be advanced with the release height. Advancing it would add
lockup keys to the legacy authority set and requires a new audit and an explicit protocol decision.

## 6. Client lifecycle and actions

- The roles view displays every valid lockup for an accepted role as an independent row.
- The general bond-management inventory also shows BONDED_ROLE lockups matching rejected proposals
  and invalid candidates, so collateral remains discoverable and confiscatable.
- Sign and verify actions are offered only for a confirmed valid lockup and always use the proposal
  transaction key. The entered profile id is transformed into the canonical bound message before
  signing or verification. The desktop creates version `1` registrations even for pre-cutoff
  lockups; version `0` exists only to keep already deployed callers operational.
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
object and remains absent from the management inventory. The same applies to a lockup carrying the
`UNDEFINED` reason ([bonds.md](bonds.md) §1), which no consumer domain selects.

Before reason-based classification, both kinds of output were collected as bonded reputation and were
therefore visible and confiscatable. That rule was wrong for the reasons in
[bonded-reputation.md](bonded-reputation.md) §1, so the gap is the accepted cost of removing it.
Closing it needs a generic bond representation which can display and confiscate such collateral
without treating it as an authorized role or as reputation.

## 8. Freshness

Repositories must rebuild independent lockup objects from current DAO state and clear their previous
contents if a rebuild fails. Bridge and REST verification must reject requests until DAO parsing is
complete and monitoring reports the local DAO state ready and in sync.

Bisq 2 retention, renewal and removal of an already accepted registration are outside this repository.
They must not assume that one successful Bisq 1 verification remains valid after the relevant
lockup state changes. Version `1` names that lockup directly; version `0` must be re-evaluated against
the remaining eligible pre-cutoff lockups and its legacy signature.
