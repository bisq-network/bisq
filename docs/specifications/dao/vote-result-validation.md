# Vote-result payload validation

## Scope

This specification defines how a DAO vote result consumes the untrusted vote data revealed from a
blind vote. It covers validation that can occur only after decryption, the failure boundary for a
malformed voter, majority blind-vote-list reconstruction, and compatibility with results evaluated
before a rule activates. Merit-claim validity is specified separately in [`merit.md`](merit.md).

## Trust boundary

The blind-vote transaction commits to the encrypted vote bytes, but the contents cannot be
validated until the secret key is published during vote reveal. Successful decryption therefore
does not make the plaintext valid. Every structural invariant of the decrypted vote list must be
checked before it is converted into ballots or included in DAO state.

Malformed plaintext belonging to one blind vote must not prevent valid, independently committed
blind votes from being evaluated. The malformed voter is excluded as one unit; individual entries
must not be merged, discarded, or otherwise repaired in a way which could assign an attacker-chosen
meaning to ambiguous input.

An undecryptable merit list is different from malformed vote plaintext. The transaction commits to
the encrypted votes but not to the merit ciphertext, so failure of the latter must not discard the
committed ballots or the on-chain stake.

## Majority blind-vote-list commitment

Each vote-reveal transaction commits to the voter's view of the complete blind-vote payload list.
The list hash covers the canonical bytes of every payload, including the encrypted merit list. Vote
result processing must therefore reconstruct and match that exact list before interpreting merit
data. It must not remove a payload merely because the payload's merit list fails to decrypt: doing so
would make a majority-committed list impossible to reconstruct because recovery can remove additional
local payloads but cannot restore one removed by validation.

From merit-decryptability activation, blind-vote payloads are ordered by:

1. blind-vote transaction ID; then
2. unsigned lexicographic order of the complete canonical payload bytes.

The second key makes the order total when several P2P payloads name the same transaction. Before this
rule, sorting only by transaction ID preserved P2P arrival order for equal IDs, so nodes holding the
same payload set could commit to different hashes. Vote reveal selects this ordering rule using the
current cycle's future result-evaluation height; vote result uses that same height directly.

## Same-transaction-ID payload selection

The blind-vote transaction authenticates `encryptedVotes`, so valid payloads sharing its transaction
ID ordinarily contain the same committed vote ciphertext. It does not authenticate
`encryptedMeritList`, allowing a forged P2P duplicate to replace only the merit data.

After the exact majority list has been matched, payloads are grouped by blind-vote transaction ID.
From merit-decryptability activation:

- a single payload is retained whether or not its merit list decrypts;
- among several payloads, the canonically first payload whose merit list decrypts under the on-chain
  reveal key supplies the vote and merit data;
- if none decrypts, the canonically first payload supplies the vote data and its merit is empty; and
- candidate selection does not remove or reorder entries in the already matched majority list.

Consequently malformed merit data can contribute no merit, but it cannot erase ballots, erase
on-chain stake, or make the majority commitment structurally unreachable.

## Proposal transaction ID uniqueness

A decrypted vote list may contain at most one entry for each proposal transaction ID. A duplicate
is ambiguous even when both entries carry the same vote, because accepting duplicates would make
the interpretation of malformed serialized input dependent on a merge policy.

From activation:

- duplicate proposal transaction IDs invalidate that blind vote;
- the invalid blind vote contributes no ballots, stake, or merit;
- other voters in the cycle continue to be evaluated; and
- the validation failure is recorded for diagnosis.

The node's valid ballot universe is defined by [`proposal-validation.md`](proposal-validation.md)
and is expected to contain only one canonical proposal for a proposal transaction ID. A conflict in
that local consensus input is a DAO-state integrity failure, not malformed data attributable to one
voter, and must not be silently resolved by choosing a ballot.

## Activation and compatibility

Both rules are selected by the result-evaluation block height of the cycle being processed. Each has
its own activation setting so it can be scheduled independently of other hard-fork rules and of the
other rule in this specification. Their initial schedules are:

| Rule | Mainnet | Bitcoin testnet | Regtest and DAO test networks |
|---|---:|---:|---:|
| Duplicate proposal transaction ID validation | `963 350` | `3 000 000` | `1` |
| Blind-vote merit decryptability and equal-ID ordering | `963 350` | `3 000 000` | `1` |

Before the respective activation, historical behavior is preserved:

- constructing the proposal-ID map throws on a duplicate and aborts the cycle's result calculation;
- payloads with an observed reveal are removed before majority matching when their merit list does
  not decrypt;
- equal transaction IDs retain P2P arrival order; and
- the first same-ID payload in the matched list is used for result processing.

These behaviors are retained only for deterministic replay of old cycles; they are not the desired
validation or failure boundaries.

The bundled mainnet resources through height `963 120` contain 935 revealed blind-vote payloads that
could be decrypted. None contains a duplicate proposal transaction ID, none has a merit-list
decryption failure, and all 947 stored blind-vote payloads have distinct transaction IDs. This audit
supports deployment confidence but does not replace the explicit height gates, and it does not cover
blocks after the resource height. The 12 stored payloads without an on-chain reveal cannot enter a
vote result and their encrypted contents could not be audited.
