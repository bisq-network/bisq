# Data stores

### Update stores

With every release we include the latest snapshot of Mainnet and Testnet data from the P2P network within the client.

* Start your Bisq client on Mainnet and Testnet and let it run until it is fully synced.
* Run [copy_dbs.sh](https://github.com/bisq-network/bisq/blob/master/desktop/package/macosx/copy_dbs.sh) to copy the
required files into the [p2p resources directory](https://github.com/bisq-network/bisq/blob/master/p2p/src/main/resources).
* To add a new trade statistic snapshot just add it to the list of trade statistic snapshots in https://github.com/bisq-network/bisq/blob/0345c795e2c227d827a1f239a323dda1250f4e69/common/src/main/java/bisq/common/app/Version.java#L40
