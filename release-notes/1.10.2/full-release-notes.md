# Bisq 1.10.2 Release Notes

These notes are written in the same practical style used by Bitcoin Core release notes: user and operator impact first, followed by a complete auditable commit inventory.

Commit range: after `5d93ef99d5a55eddc979b324f981ef8ab500b487` through `ff36958467e3d7f95434f30094c33433c590c05a`, generated with `git log --reverse v1.10.1..v1.10.2`. This excludes the `v1.10.1` tag commit and includes the `v1.10.2` version commit.

- Total commits: 14
- Non-merge commits: 13
- Merge commits: 1
- Files changed: 26
- Generated on: 2026-06-19

## Compatibility Notes

- Bisq version is set to `1.10.2`.
- DAO merit consensus V2 activates at block `954_200`.
- Before block `954_200`, DAO merit and arithmetic code paths preserve legacy behavior for historical replay compatibility.
- At and after block `954_200`, merit used for vote weight is validated against DAO state compensation issuances instead of trusting the embedded merit list alone.
- At and after block `954_200`, DAO vote-result arithmetic uses exact integer arithmetic and records invalid or overflowing consensus calculations as vote-result failures instead of silently wrapping.
- This is a consensus-critical release. Release validation should include a full mainnet DAO replay and DAO state hash verification. If historical DAO state already contains data that V2 validation evaluates differently, the release must be treated as an intentional coordinated consensus upgrade at the activation height.

## Notable Changes

### DAO Merit Validation

Merit validation is split into legacy and V2 implementations. The legacy path keeps historical behavior before activation. The V2 path validates each compensation issuance from the untrusted merit list against the DAO state, rejects non-compensation issuances, ignores future merit, checks signatures, and prevents duplicate issuance tx IDs from contributing more than once.

`Issuance` now has explicit value equality so the V2 path can compare an embedded merit issuance with the DAO state issuance by value.

### Height-Gated DAO Arithmetic

The DAO state model now routes arithmetic through `DaoArithmetics`. Before activation it uses legacy primitive arithmetic to preserve historical replay. At and after activation it uses exact arithmetic for integer and long addition, multiplication, division, and multiply-then-divide calculations.

Height-aware arithmetic is applied to proposal vote result quorum and threshold calculations, accepted and rejected stake totals, vote-result stake aggregation, majority-hash stake totals, and issuance total checks.

### Vote Result Consensus Handling

Vote-result processing now carries the chain height into consensus arithmetic so the correct legacy or V2 behavior is selected. Arithmetic overflow in V2 vote-result calculations is intentionally allowed to surface through the vote-result exception boundary so the affected cycle is treated as failed instead of accepting wrapped values.

The majority-hash calculation now rejects a zero total stake before dividing by total stake. This prevents an invalid divide-by-zero majority check.

### DAO Results Display

The desktop DAO result view now passes the result-phase height into displayed proposal results, so displayed quorum and threshold arithmetic matches the consensus height being shown.

The cycle accepted-vote count now uses `getNumAcceptedVotes()` instead of `getNumActiveVotes()`, fixing a pre-existing display bug where rejected votes were included in the accepted-vote total.

### Tests

Regression coverage was added for the merit consensus activation boundary, V2 merit validation, duplicate and forged merit handling, DAO arithmetic legacy and V2 behavior, proposal vote result arithmetic, vote-result consensus stake aggregation, vote-result service arithmetic, and legacy versus V2 weighted merit calculations.

### Release Version

The release is finalized by setting the application and packaging version to `1.10.2`.

## Complete Commit Inventory

Rows are ordered by `git log --reverse` over the release range.

| Date | Commit | Type | Summary | Author |
| --- | --- | --- | --- | --- |
| 2026-06-18 | [cc5e6b81b0](https://github.com/bisq-network/bisq/commit/cc5e6b81b0c51a64c2c8cb11d412f35ee95eaa16) | Commit | Add value equality for DAO issuances | HenrikJannsen |
| 2026-06-18 | [8189d90dcd](https://github.com/bisq-network/bisq/commit/8189d90dcdd49748c07c3412c51800abb260d56c) | Commit | Split legacy and V2 merit consensus | HenrikJannsen |
| 2026-06-18 | [71c71069e5](https://github.com/bisq-network/bisq/commit/71c71069e5428541d213826a7a1dc054db3ad1d7) | Commit | Add height-gated DAO arithmetic helpers | HenrikJannsen |
| 2026-06-18 | [0670418898](https://github.com/bisq-network/bisq/commit/06704188983a2240cb16932a3838d1a363a8d328) | Commit | Use height-aware arithmetic in proposal results | HenrikJannsen |
| 2026-06-18 | [86bbf4817a](https://github.com/bisq-network/bisq/commit/86bbf4817aa704a00a390dcac5895375f58ddf00) | Commit | Pass cycle height to displayed proposal results | HenrikJannsen |
| 2026-06-18 | [ab0428522b](https://github.com/bisq-network/bisq/commit/ab0428522bcb395eabcd0dd87ab1d5de96df991b) | Commit | Treat non-positive accepted stake as zero threshold | HenrikJannsen |
| 2026-06-18 | [3364d501d0](https://github.com/bisq-network/bisq/commit/3364d501d0b767ae8c3f8044885bbf08ebfb1765) | Commit | Use exact arithmetic in V2 merit weighting | HenrikJannsen |
| 2026-06-18 | [77cc8017ef](https://github.com/bisq-network/bisq/commit/77cc8017efe44b223cd59bae24a593c8bed22bb9) | Commit | Apply height-gated arithmetic to vote results | HenrikJannsen |
| 2026-06-18 | [114faa4cef](https://github.com/bisq-network/bisq/commit/114faa4cefb4e88b52e89c467c2aa3ba716b1e05) | Commit | Add DAO merit and arithmetic regression tests | HenrikJannsen |
| 2026-06-18 | [a26dc65174](https://github.com/bisq-network/bisq/commit/a26dc65174bccba91b1c5057c0b8ad40bb4679da) | Commit | Address merit hardening review follow-ups | HenrikJannsen |
| 2026-06-18 | [f11399ed4e](https://github.com/bisq-network/bisq/commit/f11399ed4e072eaa5afe7d7847fa3d6fc395613b) | Commit | Fix pre-existing bug with using getNumActiveVotes instead of getNumAcceptedVotes. | HenrikJannsen |
| 2026-06-18 | [fa5470a984](https://github.com/bisq-network/bisq/commit/fa5470a984e1e3f4b204ac95c8aa910394dcc543) | Commit | Add check that stakeOfAll must not be 0 as used in division. | HenrikJannsen |
| 2026-06-18 | [622841b048](https://github.com/bisq-network/bisq/commit/622841b048c4f356738c80aca5c046c83f131154) | Merge | Merge pull request #7925 from HenrikJannsen/harden-merit-validation | Alejandro García |
| 2026-06-18 | [ff36958467](https://github.com/bisq-network/bisq/commit/ff36958467e3d7f95434f30094c33433c590c05a) | Commit | Bump version number for v1.10.2 | Alejandro García |
