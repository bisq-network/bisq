# Bisq 1.10.3 Highlights

Short text for GitHub release notes and the in-app update message.

## Filter Safety

- Filter-provided added BTC nodes and seed nodes are disabled because those fields are not currently covered by the filter signature.
- Previously persisted `filterProvidedBtcNodes` and `filterProvidedSeedNodes` config values are ignored on startup.
- Filter-provided BTC fee receiver addresses are temporarily ignored; DAO donation addresses and Burning Man receiver addresses remain active.

## Trade And API Safety

- The Core API rejects payment-start confirmation for trades that are already failed.
- Buyer-side deposit/DPT processing checks that the peer deposit transaction matches the local transaction before wallet or trade-state mutation.
- Delayed payout transactions must spend output zero of the matching deposit transaction.
- Stale deposit confirmation callbacks are ignored when they refer to an older deposit transaction.

## Message Authentication

- Trade chat and ACK messages must be signed by the expected trade peer.
- Dispute opening messages must be signed by the declared dispute opener.
- Peer-opened dispute and dispute-result messages must be signed by the locally expected mediation, refund, or arbitration agent.
- Invalid messages are ignored before dispute, chat, or persistence state is mutated.

## Tests And Version

- Regression tests cover deposit/DPT binding, delayed payout input checks, buyer-as-maker deposit processing, ACK authentication, dispute opener authentication, dispute agent authentication, and mediation dispute-result authentication.
- The app and packaging version is `1.10.3`.
