# Bisq 1.10.2 Highlights

Short text for GitHub release notes and the in-app update message.

## DAO Consensus Security

- DAO merit validation is hardened at and after block `954_200`.
- V2 merit validation checks embedded merit issuances against DAO state compensation issuances, verifies signatures, ignores future merit, and prevents duplicate issuance tx IDs from contributing more than once.
- Legacy merit behavior remains in place before block `954_200` for historical replay compatibility.

## Arithmetic Hardening

- DAO consensus arithmetic is height-gated: legacy arithmetic before block `954_200`, exact V2 arithmetic at and after activation.
- Exact arithmetic is applied to proposal quorum and threshold calculations, vote-result stake aggregation, majority-hash stake totals, issuance sum checks, and V2 weighted merit calculations.
- A zero total-stake majority hash case is now rejected before division.

## DAO Results And Tests

- DAO result display calculations now use the result-phase height.
- The cycle accepted-vote count now uses accepted votes instead of active votes.
- Regression tests cover merit validation, activation boundaries, DAO arithmetic, proposal vote results, vote-result consensus, vote-result service aggregation, and legacy versus V2 weighted merit.

## Version And Operator Note

- The app and packaging version is `1.10.2`.
- This release changes DAO consensus behavior at block `954_200`. Release validation should include full mainnet DAO replay and DAO state hash verification, or the release must be coordinated as an intentional activation-height consensus upgrade.
