# Bisq 1.10.10 Highlights

## DAO Resources

- Updated bundled mainnet DAO state and block data through height 971800.
- Added DAO state hash checkpoints for heights 970000 and 971000; nodes with a diverging DAO state resync from the bundled data.
- Added the Burning Man address list v0007.
- Stopped shipping the short-lived temporary proposal store.

## Tor and Security

- Updated the netlayer dependency to v0.7.12, which includes Tor 0.4.9.14.
- Renewed the validity of the release signing keys and removed the unused, expired key `4A133008`.

## Builds

- Updated CI actions.
- The application and packaging version is `1.10.10`.
