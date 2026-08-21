# Bisq 1.10.3 Notable Changes

This file filters the full release notes down to the most relevant security, API, and operator changes. Each section gives a short summary first and then lists representative related commits.

## Unsigned Filter Fields Disabled

Filter-provided added BTC nodes and seed nodes are no longer persisted or applied because those fields are not covered by the current filter signature. Previously persisted `filterProvidedBtcNodes` and `filterProvidedSeedNodes` config values are also ignored on startup.

Related commits:
- [358b546526](https://github.com/bisq-network/bisq/commit/358b546526d07445a857aa70da76cc1acdeca53b) - Disable persistence of filter-provided BTC and seed nodes.
- [8aeddc7bb5](https://github.com/bisq-network/bisq/commit/8aeddc7bb51b28926316fedcf065eda4385b08ee) - Ignore config values for filter-provided BTC and seed nodes.
- [9d2c952555](https://github.com/bisq-network/bisq/commit/9d2c95255508617688f29f07c9adcd4c155df258) - Merge the filter-provided node deactivation.

## BTC Fee Receiver Filter Entries Disabled

Filter-provided `btcFeeReceiverAddresses` are temporarily ignored by the mempool service. DAO donation addresses and Burning Man receiver addresses remain in the BTC fee receiver list.

Related commits:
- [ca462d1f48](https://github.com/bisq-network/bisq/commit/ca462d1f4884559602552c3421fcf87e7d5092e3) - Temporarily disable filter-provided BTC fee receiver addresses.
- [802ab0e129](https://github.com/bisq-network/bisq/commit/802ab0e129cea7e9ec7612d7f6b04daa59fecc4a) - Merge the BTC fee receiver filter deactivation.

## Failed Trade API Guard

The Core API now rejects attempts to confirm payment started for a failed trade. This prevents API clients from moving failed trades forward through the buyer protocol path.

Related commits:
- [e6ecc74f6e](https://github.com/bisq-network/bisq/commit/e6ecc74f6e752205585d0f9c18bf494bef5154b8) - Prevent failed trades from moving to payment-started state through the API.
- [c7b68d485f](https://github.com/bisq-network/bisq/commit/c7b68d485ff593c21bbb585cdf3ec4252841bd58) - Merge the failed-trade payment-start guard.

## Deposit And Delayed Payout Transaction Binding

Buyer-side processing now checks that the peer deposit transaction matches the buyer's local transaction, that the delayed payout transaction spends output zero of that deposit transaction, and that stale deposit confirmation callbacks cannot advance a trade after its current deposit transaction has changed.

The deposit transaction listener now requires distinct maker-fee and taker-fee inputs where both are known, instead of accepting a simple count of matching fee transaction IDs.

Related commits:
- [7e7e4ed905](https://github.com/bisq-network/bisq/commit/7e7e4ed905fd30211deb861619fed572a529c2f4) - Harden deposit transaction and delayed payout transaction binding.

## Support And Dispute Message Authentication

Support message handling now authenticates decrypted sender signature public keys against local expectations. Trade chat and ACK messages must come from the expected trade peer. Dispute openers must match the declared trader in the contract. Peer-opened dispute and dispute-result messages must come from the locally expected mediation, refund, or arbitration agent, and payload agent keys must match the local trade.

Invalid or unauthenticated messages are ignored before dispute lists, chat state, or persistence are mutated.

Related commits:
- [292f438112](https://github.com/bisq-network/bisq/commit/292f438112b3c721a1a73ff2eb8c3ed8ac7fdce6) - Harden support and dispute message sender authentication.

## Regression Tests

The release adds focused regression tests for deposit/DPT binding, delayed payout transaction input validation, buyer-as-maker deposit processing, ACK authentication, dispute opener authentication, dispute agent authentication, and mediation dispute-result authentication.

Related commits:
- [7e7e4ed905](https://github.com/bisq-network/bisq/commit/7e7e4ed905fd30211deb861619fed572a529c2f4) - Add deposit/DPT binding regression coverage.
- [292f438112](https://github.com/bisq-network/bisq/commit/292f438112b3c721a1a73ff2eb8c3ed8ac7fdce6) - Add support and dispute authentication regression coverage.

## Release Version

The application and packaging version were bumped to `1.10.3`, and the final tag includes the subsequent security-hardening commits.

Related commits:
- [7a441aa6fc](https://github.com/bisq-network/bisq/commit/7a441aa6fc4aba28c8a9692d8bf48ec50e075fd7) - Bump version number for `v1.10.3`.

## Release Gate

This is a security-hardening release. Release validation should include focused tests for the filter/config paths, Core API failed-trade guard, deposit/DPT binding, and support/dispute message authentication.
