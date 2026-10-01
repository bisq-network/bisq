# Bisq 1.10.9 Highlights

## Trading and Payments

- The API prevents payment-started confirmation before seller payment details arrive.
- Revolut account details use the Revtag identifier.
- Offer republishing waits for P2P bootstrap to complete.

## Reliability

- Trusted BSQ block providers are initialized once, including when the configured list is empty.
- Bank account input validation runs before country-dependent fields are cleared.
- P2P offer expiry backdating is limited to outbound disconnects, and DAO seed state hashes are trusted only over outbound connections.

## BSQ Swaps

- Validate BSQ swap inputs and signatures at both sides of the trade, including outpoint binding and duplicate input checks.

## DAO Resources

- Updated bundled mainnet DAO state and block data through height 970000.
- Refreshed DAO state hash checkpoints.
- Updated the Burning Man address list and BTC mainnet denylist.

## Tor and Builds

- Updated the netlayer dependency to v0.7.11, which includes Tor 0.4.9.13.
- Improved Gradle configuration-cache and build-cache support and updated the reproducible Debian Docker build.
- The application and packaging version is `1.10.9`.
