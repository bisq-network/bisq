# Bisq 1.10.3 Release Notes

These notes are written in the same practical style used by Bitcoin Core release notes: user and operator impact first, followed by a complete auditable commit inventory.

Commit range: from `358b546526d07445a857aa70da76cc1acdeca53b` through `292f438112b3c721a1a73ff2eb8c3ed8ac7fdce6`, generated with `git log --reverse 358b546526d07445a857aa70da76cc1acdeca53b^..v1.10.3`. This includes the requested start commit and all later commits in tag `v1.10.3`.

- Total commits: 10
- Non-merge commits: 7
- Merge commits: 3
- Files changed: 27
- Generated on: 2026-07-06

## Compatibility Notes

- Bisq version is set to `1.10.3`.
- Filter-provided added BTC nodes and seed nodes are disabled because those fields are not included in the current filter signature.
- Previously persisted `filterProvidedBtcNodes` and `filterProvidedSeedNodes` config values are ignored in this release.
- Filter-provided BTC fee receiver addresses are temporarily ignored. The mempool fee receiver list still includes DAO donation addresses and Burning Man receiver addresses.
- The Core API now rejects payment-start confirmation for trades that are already marked as failed.
- Deposit transaction and delayed payout transaction processing now performs stricter binding checks before wallet or trade-state mutation.
- Trade chat, ACK, dispute opening, peer-opened dispute, and dispute-result messages are ignored when the sender signature public key does not match the expected peer, trader, or dispute agent.
- This is a security-hardening release. Operators should upgrade promptly, especially nodes that rely on the filter, API trade control, or dispute-message processing paths.

## Notable Changes

### Unsigned Filter Fields Disabled

Bisq no longer persists or applies filter-provided added BTC nodes and seed nodes. The existing filter fields are currently outside the signed filter payload, so values from those fields are treated as unsafe and written/applied as empty values until the replacement filter implementation is available.

Configuration values previously written from these fields are also ignored by `Config`, preventing stale persisted values from being applied on startup.

### BTC Fee Receiver Filter Entries Disabled

Filter-provided `btcFeeReceiverAddresses` are temporarily excluded from the mempool service's BTC fee receiver list. The path was not actively used and is expected to be handled by the new filter implementation. DAO donation addresses and Burning Man receiver addresses continue to be used.

The linked `BtcFeeReceiverService.java` file was not changed in this range; the related fee receiver behavior changed in `MempoolService`.

### Failed Trade API Guard

`CoreTradesService.confirmPaymentStarted` now rejects attempts to move a failed trade into the payment-started state. This prevents API clients from advancing a failed trade through the buyer protocol path.

### Deposit And Delayed Payout Transaction Binding

Buyer-side processing of `DepositTxAndDelayedPayoutTxMessage` now verifies the peer's deposit transaction against the buyer's local deposit transaction before mutating wallet or trade state. For buyer-as-maker trades, the prepared deposit transaction is used because the maker has no deposit transaction set yet.

The delayed payout transaction is also checked against the peer deposit transaction and must spend output zero of that deposit transaction. This prevents accepting a delayed payout transaction that is correctly serialized but bound to a different deposit transaction.

Deposit confirmation callbacks are bound to the transaction ID they were registered for. If a stale callback fires after the trade's current deposit transaction has changed, the callback is ignored instead of marking the trade as deposit-confirmed.

The deposit transaction listener now requires one distinct maker-fee input and, when the taker fee transaction ID is known, one distinct taker-fee input. This avoids accepting a transaction that satisfies a simple input-count check with multiple outputs from only one fee transaction.

### Support Message Authentication

Support message handling now carries the decrypted sender signature public key through direct and mailbox message processing.

Trade chat and ACK handling verify that the sender signature public key matches the expected peer from the local trade context. ACK messages whose source message cannot be found are ignored instead of resolving a peer key from untrusted message data.

Dispute opening validates that the signed sender matches the declared dispute opener in the embedded contract, and rejects disputes whose trader key does not match the declared opener. Peer-opened dispute and dispute-result messages validate the sender against the locally expected mediation, refund, or arbitration agent key from the trade record, and reject payloads whose agent key does not match the local trade.

Invalid dispute data is now rejected before the dispute list is mutated or persisted.

### Tests

Regression coverage was added for delayed payout transaction input binding, mismatched deposit transaction rejection, buyer-as-maker deposit processing, stale deposit confirmation handling, support ACK authentication, dispute opener authentication, dispute agent authentication, failed-trade lookup during dispute authentication, and mediation dispute-result sender authentication.

### Release Version

The application and packaging version are bumped to `1.10.3`. The `v1.10.3` tag includes the version bump plus the subsequent deposit/DPT and dispute-message hardening commits.

## Complete Commit Inventory

Rows are ordered by `git log --reverse` over the release range.

| Date | Commit | Type | Summary | Author |
| --- | --- | --- | --- | --- |
| 2026-07-04 | [358b546526](https://github.com/bisq-network/bisq/commit/358b546526d07445a857aa70da76cc1acdeca53b) | Commit | Disable persistence of filter-provided BTC and seed nodes | HenrikJannsen |
| 2026-07-04 | [ca462d1f48](https://github.com/bisq-network/bisq/commit/ca462d1f4884559602552c3421fcf87e7d5092e3) | Commit | Temporarily disable filter-provided BTC fee receiver addresses | HenrikJannsen |
| 2026-07-04 | [e6ecc74f6e](https://github.com/bisq-network/bisq/commit/e6ecc74f6e752205585d0f9c18bf494bef5154b8) | Commit | Prevent failed trades from moving to payment-started state through the API | HenrikJannsen |
| 2026-07-04 | [8aeddc7bb5](https://github.com/bisq-network/bisq/commit/8aeddc7bb51b28926316fedcf065eda4385b08ee) | Commit | Ignore config values for filter-provided BTC and seed nodes | HenrikJannsen |
| 2026-07-05 | [9d2c952555](https://github.com/bisq-network/bisq/commit/9d2c95255508617688f29f07c9adcd4c155df258) | Merge | Merge unsigned filter-provided seed/BTC node deactivation | Alejandro Garcia |
| 2026-07-05 | [802ab0e129](https://github.com/bisq-network/bisq/commit/802ab0e129cea7e9ec7612d7f6b04daa59fecc4a) | Merge | Merge BTC fee receiver filter deactivation | Alejandro Garcia |
| 2026-07-05 | [c7b68d485f](https://github.com/bisq-network/bisq/commit/c7b68d485ff593c21bbb585cdf3ec4252841bd58) | Merge | Merge failed-trade payment-start API guard | Alejandro Garcia |
| 2026-07-05 | [7a441aa6fc](https://github.com/bisq-network/bisq/commit/7a441aa6fc4aba28c8a9692d8bf48ec50e075fd7) | Commit | Bump version number for `v1.10.3` | Alejandro Garcia |
| 2026-07-05 | [7e7e4ed905](https://github.com/bisq-network/bisq/commit/7e7e4ed905fd30211deb861619fed572a529c2f4) | Commit | Harden deposit transaction and delayed payout transaction binding | Alejandro Garcia |
| 2026-07-05 | [292f438112](https://github.com/bisq-network/bisq/commit/292f438112b3c721a1a73ff2eb8c3ed8ac7fdce6) | Commit | Harden support and dispute message sender authentication | Alejandro Garcia |
