# DAO proposal validation

## Scope

This specification defines when an append-only DAO proposal may enter a node's proposal cache and
when it is eligible for proposal-state hashing and ballots. It covers startup parsing, live payload
arrival, persisted ballots, activation, and historical compatibility. Proposal-type-specific rules
may impose additional requirements; bonded-role terms are specified in
[`bonded-roles.md`](bonded-roles.md).

## Trust and commitment boundary

An append-only proposal payload is untrusted network data. Persistence and rebroadcast do not make
it valid. A proposal transaction commits to the payload through its OP_RETURN hash, but that binding
only proves that the transaction creator chose those exact fields. It does not prove that the fields
satisfy DAO rules.

Every consensus-eligible proposal must have the required transaction type and an OP_RETURN
commitment matching its canonical payload. From proposal-data-field-validation activation, it must
also pass all common and proposal-type-specific data-field rules.

Common rules that do not require parsed DAO state include:

- non-empty names and links, each no longer than 200 characters;
- a transaction ID that is either not yet assigned or is exactly 64 characters; and
- a valid bounded extra-data map.

Type-specific rules include such properties as request amounts and addresses, parameter values,
bond identifiers, asset symbols, and bonded-role terms. A type-specific rule may read the DAO
parameter value effective at the proposal transaction height.

## Append-only payload admission

The append-only P2P store is raw storage, not the consensus proposal set. A node projects stored
payloads into its in-memory proposal collection as follows:

1. Before initial DAO parsing is complete, every payload must pass the parse-independent common
   rules before entering the collection. Type-specific rules that require DAO state are deferred.
2. Once parsing is complete, newly projected payloads from an activated cycle must pass the full
   data-field validator. Pre-activation payloads continue to use the common rules so cache admission
   does not retroactively impose proposal-type rules on historical cycles.
3. Payloads provisionally projected during parsing must be rechecked under their cycle's rule when
   parsing completes, even if the collection already contains them. Invalid entries are removed.

The collection remains available during historical parsing because proposal-state hashes are built
at the first blind-vote block of each replayed cycle. Deferring every payload until parsing completes
would incorrectly produce empty historical proposal sets. Provisional projection therefore never
substitutes for validation at the consensus consumer.

These rules make the final in-memory collection independent of whether a payload was first observed
from disk during startup or from the network after startup.

## Consensus eligibility

Consensus consumers must validate proposals independently of cache admission and ballot
persistence. In particular, the same eligibility boundary applies to:

- the proposal list used by proposal-state hashing;
- ballots offered for voting;
- the valid ballot universe reconstructed during vote-result calculation; and
- ballots restored from an older local persistence file.

From activation, full data-field validation is performed even during initial blockchain parsing.
Validation must not depend on the node-wide "initial parsing complete" flag. At the point a proposal
is used for its cycle, its transaction and the DAO parameter history needed to validate it are
already available. A change-parameter proposal therefore receives the same full consensus check on
a syncing node as on a node which was already running.

An invalid proposal is omitted from both proposal-state hashing and the valid ballot universe. A
persisted ballot cannot restore it. This makes startup history, P2P arrival order, and old local
ballot persistence irrelevant to the consensus result.

## Activation and compatibility

Proposal-data-field validation has activation settings independent of bonded-role validation and
other DAO hard-fork rules:

| Network | Activation height |
|---|---:|
| Mainnet | `963 350` |
| Bitcoin testnet | `3 000 000` |
| Regtest and DAO test networks | `1` |

The first RESULT block of the proposal transaction's cycle selects the rule. This treats every
proposal in one cycle identically and prevents nodes creating ballots at different blocks within the
cycle from selecting different rules. The first cycle whose result-evaluation height is at or above
the configured height uses full data-field validation.

Before activation, the general consensus eligibility check preserves transaction-type and payload-
commitment validation without retroactively making every UI/admission rule a consensus rule.
Independently activated proposal-type rules, including bonded-role term validation, continue to use
their own specified activation semantics.

The bundled mainnet resources through height `963 120` contain 1,989 append-only proposal payloads.
None fails the common name, link, transaction-ID, or extra-data validation, and no two payloads use
the same proposal transaction ID. This audit supports historical compatibility of the admission
hardening but does not replace the activation gate or cover later blocks.

## Security rationale

Without these rules, a payload with valid transaction binding but invalid fields can be admitted by
a restarting node while a continuously running node rejects it. The two nodes then construct
different ballot universes. One can add an implicit rejection while the other treats an explicit
vote as missing and drops that voter's entire decrypted ballot set, potentially changing unrelated
proposal outcomes and the DAO state hash.

The correction shortens that chain at both required boundaries: stable invalid fields cannot enter
the startup collection, and activated full validation prevents any provisional or persisted entry
from entering consensus.
