# Bisq 1.10.2 Notable Changes

This file filters the full release notes down to the most relevant security, consensus, and operator changes. Each section gives a short summary first and then lists representative related commits.

## DAO Merit Validation

DAO merit consensus now has separate legacy and V2 implementations. Legacy behavior remains active before block `954_200`. At and after block `954_200`, V2 merit validation checks embedded merit issuances against DAO state compensation issuances, rejects non-compensation issuances, ignores future merit, verifies merit signatures, and prevents duplicate issuance tx IDs from contributing more than once.

Related commits:
- [cc5e6b81b0](https://github.com/bisq-network/bisq/commit/cc5e6b81b0c51a64c2c8cb11d412f35ee95eaa16) - Add value equality for DAO issuances.
- [8189d90dcd](https://github.com/bisq-network/bisq/commit/8189d90dcdd49748c07c3412c51800abb260d56c) - Split legacy and V2 merit consensus.
- [3364d501d0](https://github.com/bisq-network/bisq/commit/3364d501d0b767ae8c3f8044885bbf08ebfb1765) - Use exact arithmetic in V2 merit weighting.
- [a26dc65174](https://github.com/bisq-network/bisq/commit/a26dc65174bccba91b1c5057c0b8ad40bb4679da) - Address merit hardening review follow-ups.

## Height-Gated DAO Arithmetic

DAO arithmetic is now routed through height-aware helpers. Historical replay keeps legacy primitive arithmetic before block `954_200`. At and after activation, V2 arithmetic uses exact integer and long operations so overflow is not silently accepted by consensus code.

The height-aware helpers are used for proposal vote result quorum and threshold calculations, active vote counts, accepted and rejected stake totals, vote-result stake aggregation, majority hash stake totals, and issuance sum checks.

Related commits:
- [71c71069e5](https://github.com/bisq-network/bisq/commit/71c71069e5428541d213826a7a1dc054db3ad1d7) - Add height-gated DAO arithmetic helpers.
- [0670418898](https://github.com/bisq-network/bisq/commit/06704188983a2240cb16932a3838d1a363a8d328) - Use height-aware arithmetic in proposal results.
- [ab0428522b](https://github.com/bisq-network/bisq/commit/ab0428522bcb395eabcd0dd87ab1d5de96df991b) - Treat non-positive accepted stake as zero threshold.
- [77cc8017ef](https://github.com/bisq-network/bisq/commit/77cc8017efe44b223cd59bae24a593c8bed22bb9) - Apply height-gated arithmetic to vote results.

## Vote Result Consensus

Vote-result consensus now evaluates arithmetic using the relevant chain height. This keeps historical behavior before activation and applies exact V2 behavior after activation. The majority-hash calculation also rejects zero total stake before dividing, preventing an invalid divide-by-zero majority check.

Related commits:
- [77cc8017ef](https://github.com/bisq-network/bisq/commit/77cc8017efe44b223cd59bae24a593c8bed22bb9) - Apply height-gated arithmetic to vote results.
- [fa5470a984](https://github.com/bisq-network/bisq/commit/fa5470a984e1e3f4b204ac95c8aa910394dcc543) - Add check that stakeOfAll must not be zero as used in division.

## DAO Results Display

The desktop DAO results view now passes the result-phase height into displayed proposal results, aligning displayed quorum and threshold calculations with the consensus height. The cycle accepted-vote count now uses accepted votes instead of active votes, so rejected votes are no longer included in that displayed total.

Related commits:
- [86bbf4817a](https://github.com/bisq-network/bisq/commit/86bbf4817aa704a00a390dcac5895375f58ddf00) - Pass cycle height to displayed proposal results.
- [f11399ed4e](https://github.com/bisq-network/bisq/commit/f11399ed4e072eaa5afe7d7847fa3d6fc395613b) - Fix pre-existing bug with using accepted votes instead of active votes.

## Regression Tests

The release adds focused regression tests for merit validation, activation-height behavior, DAO arithmetic, proposal vote result calculations, vote-result consensus, vote-result service aggregation, and weighted merit calculations in legacy and V2 modes.

Related commits:
- [114faa4cef](https://github.com/bisq-network/bisq/commit/114faa4cefb4e88b52e89c467c2aa3ba716b1e05) - Add DAO merit and arithmetic regression tests.
- [a26dc65174](https://github.com/bisq-network/bisq/commit/a26dc65174bccba91b1c5057c0b8ad40bb4679da) - Add review follow-up coverage.

## Release Version

The DAO hardening branch was merged for `1.10.2`, and the application and packaging version were bumped to `1.10.2`.

Related commits:
- [622841b048](https://github.com/bisq-network/bisq/commit/622841b048c4f356738c80aca5c046c83f131154) - Merge the DAO merit hardening branch.
- [ff36958467](https://github.com/bisq-network/bisq/commit/ff36958467e3d7f95434f30094c33433c590c05a) - Bump version number for `v1.10.2`.

## Release Gate

This is a consensus-critical release. Release validation should include a full mainnet DAO replay and DAO state hash verification. If historical DAO state already contains data that V2 validation evaluates differently, the release must be treated as an intentional coordinated consensus upgrade at the activation height instead of a silent patch.
