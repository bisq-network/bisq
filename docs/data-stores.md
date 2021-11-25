# Data stores

### Update stores

With every release we include the latest snapshot of Mainnet data from the P2P network within the client.

* Start your Bisq client (full node with local Bitcoin core node) on Mainnet and let it run until it is fully synced.
  Sometimes it is necessary to restart the client multiple times to receive all trade statistic objects (max 3000 can be
  requested in one request). Compare the hashes in the `DAO > Network Monitor` and total number of trade statistics with
  at least one additional light client.
* Run [copy_dbs.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/copy_dbs.sh) to copy the
  required files into
  the [p2p resources directory](https://github.com/bisq-network/bisq/blob/master/p2p/src/main/resources).
* To add a new trade statistic snapshot, just add it to the list of trade statistic snapshots
  in https://github.com/bisq-network/bisq/blob/0345c795e2c227d827a1f239a323dda1250f4e69/common/src/main/java/bisq/common/app/Version.java#L40
* Create a PR against the release branch for review containing screenshots of a full and a light node for hashes
  verification.
