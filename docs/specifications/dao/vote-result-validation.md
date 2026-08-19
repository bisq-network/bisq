# Vote-result payload validation

## Scope

This specification defines how a DAO vote result consumes the untrusted vote data revealed from a
blind vote. It covers validation that can occur only after decryption, the failure boundary for a
malformed voter, and compatibility with results evaluated before a rule activates. Majority blind
vote-list reconstruction is outside the current scope; merit validation is specified separately.

## Trust boundary

The blind-vote transaction commits to the encrypted vote bytes, but the contents cannot be
validated until the secret key is published during vote reveal. Successful decryption therefore
does not make the plaintext valid. Every structural invariant of the decrypted vote list must be
checked before it is converted into ballots or included in DAO state.

Malformed plaintext belonging to one blind vote must not prevent valid, independently committed
blind votes from being evaluated. The malformed voter is excluded as one unit; individual entries
must not be merged, discarded, or otherwise repaired in a way which could assign an attacker-chosen
meaning to ambiguous input.

## Proposal transaction ID uniqueness

A decrypted vote list may contain at most one entry for each proposal transaction ID. A duplicate
is ambiguous even when both entries carry the same vote, because accepting duplicates would make
the interpretation of malformed serialized input dependent on a merge policy.

From activation:

- duplicate proposal transaction IDs invalidate that blind vote;
- the invalid blind vote contributes no ballots, stake, or merit;
- other voters in the cycle continue to be evaluated; and
- the validation failure is recorded for diagnosis.

The node's valid ballot universe is expected to contain only one canonical proposal for a proposal
transaction ID. A conflict in that local consensus input is a DAO-state integrity failure, not
malformed data attributable to one voter, and must not be silently resolved by choosing a ballot.

## Activation and compatibility

Duplicate proposal transaction ID validation is selected by the result-evaluation block height of
the cycle being processed. It has a dedicated activation setting so it can be scheduled independently
of other hard-fork rules:

| Network | Activation height |
|---|---:|
| Bitcoin mainnet | `963 350` |
| Bitcoin testnet | `3 000 000` |
| Regtest and DAO test networks | `1` |

Before activation, historical behavior is preserved: constructing the proposal-ID map throws on a
duplicate and aborts the cycle's result calculation. This behavior is intentionally retained only
for deterministic replay of old cycles; it is not the desired failure boundary.

The bundled mainnet resources through height `963 120` contain 935 revealed blind-vote payloads that
could be decrypted. None contains a duplicate proposal transaction ID. This audit supports deployment
confidence but does not replace the explicit height gate, and it does not cover blocks after the
resource height.
