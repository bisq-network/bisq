# Bisq 1.10.12 Highlights

> **Draft. The maintainer completes it before the release** (see the list at the top of `full-release-notes.md`):
> it covers `release-candidate-v1.10.12` through `d2d9bf479a`. Confirm the version number and set the dates.

## New Payment Methods

- Stablecoins in the USD market: USDT, USDC, USDS, USDe, DAI, RLUSD, USDG, PYUSD, U and USDF on Ethereum, Tron, BNB
  Smart Chain and Solana, 18 methods in all. One coin pays one USD. The trade period is 24 hours, and the highest
  trade limit applies without account age or signing.
- A custom payment method for payment methods which Bisq does not support, in every fiat currency, with a trade limit
  of 0.01 BTC.
- Bisq 1.10.11 and earlier cannot take these offers. Mediators and refund agents update before the first such trade.

## BSQ

- The BSQ market price is the volume weighted average of the BSQ trades of the last 24 hours, not the price of the
  newest trade.
- The BSQ offer book hides offers whose price is far worse than the 90-day average. The factor is set in the
  preferences; 1000 turns it off.

## Trades and Disputes

- When both traders accepted a mediation result, the mediated payout is published without a second click, also
  after a restart.
- The first mediation result fixes the payout.
- The take offer screen names the maker's reason for a refusal, and a fee which the maker would refuse is reported
  before the maker is contacted.
- Many checks of the deposit transaction, the disputes and the payment confirmations protect traders against
  modified clients of the peer.

## Fees

- The default fee rates drop to 5 sat/vB, and the fee rate never goes below 2 sat/vB. Makers accept taker fees
  within a factor of 4.

## Privacy and Security

- The daemon requires `--apiPassword`, and the API listens only on `127.0.0.1` unless `--apiHost` says otherwise.
- The data directory is owner-only on Linux and macOS.
- Incoming messages are size-checked before they are read, and malformed network data, offers, trade statistics,
  alerts and mailbox entries are rejected.
- Alerts need a signature over all their fields; alerts of earlier versions are ignored.

## DAO

- A proposal without votes is no longer part of the vote result. All seed nodes run 1.10.12 before the next RESULT
  block.
- Full nodes send signed blocks only and no longer stop at unknown script types.
- While a resync is needed, trading and DAO actions are blocked instead of a popup every 30 seconds.

## Tor

- tor 0.4.9.16 of Tor Browser 16.0.1 and sandy 1.2.0.
- When Tor cannot run in the sandbox, Bisq offers to turn it off.
- The logs of Tor go into one file, `tor.log`.

## Operators

- Seed nodes update first, before the other nodes.
- Seed nodes shut down at the second loss of all connections and need an automatic restart.
- Publish alerts with 1.10.12.
- Builds use Amazon Corretto 21.0.13.1 or newer.

## Short Text for the Update Message

Bisq 1.10.12 adds stablecoins (USDT, USDC and more) and custom payment methods to the USD and fiat markets, uses the
average of recent trades as the BSQ price, publishes mediated payouts automatically once both traders accepted, and
lowers the default fee rates. It contains many security fixes for trades, disputes, the network and the DAO. Please
update.
