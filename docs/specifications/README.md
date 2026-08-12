# Domain specifications

This directory is the canonical location for specifications of intended domain behaviour.

A specification describes **what** the system must do and **why**. It is authoritative for intended
behaviour and takes precedence over behaviour inferred from the current implementation or from tests.
Implementation-oriented documentation ("how a particular part works") belongs in package-local
`*.md` files or in `docs/`, and should reference the specification here rather than duplicating it.

Where a rule is currently unspecified, disputed, or known to be violated by the implementation, the
specification says so explicitly instead of describing the implementation as if it were intended.

## Index

### `application/`

| Specification | Covers |
|---|---|
| [`application/graceful-shutdown.md`](application/graceful-shutdown.md) | Graceful application shutdown, persistence completion, controlled process exit, and the JVM shutdown-hook backstop. |


### `dao/`

| Specification | Covers |
|---|---|
| [`dao/bonds.md`](dao/bonds.md) | The BSQ bond primitive: lockup and unlock transactions, the bond state machine, slashing/confiscation, and the invariants every bond consumer may rely on. |
| [`dao/bond-lockup-spend.md`](dao/bond-lockup-spend.md) | The consensus rule for spending bond lockup outputs, including hard-fork activation, invalid-spend burning, and parsed block-data integrity. |
| [`dao/bonded-roles.md`](dao/bonded-roles.md) | Role proposals, independent role-lockup lifecycles, and proposal-key registrations bound to exact collateral for Bisq 2. |
| [`dao/bonded-reputation.md`](dao/bonded-reputation.md) | Reputation lockups, how they are collected, and what the Bisq 1 → Bisq 2 bridge exports. |
| [`dao/merit.md`](dao/merit.md) | Merit as vote weight: what a merit claim must prove against DAO state, decay, uniqueness per cycle, and the activation boundary. |
| [`dao/dao-state-checkpoints.md`](dao/dao-state-checkpoints.md) | Bundled DAO state hashes, verification boundaries, mismatch recovery, and checkpoint generation. |

### `trade/`

| Specification | Covers |
|---|---|
| [`trade/fiat-buyer-payment-account-validation.md`](trade/fiat-buyer-payment-account-validation.md) | Contract-bound buyer payment-account validation and the fiat deposit/settlement gates that depend on it. |
