Setup or RPC calls to Bitcoin Core
====================

You need to setup the bitcoin.conf and add a blocknotify sh file. See the examples in the rec_regtest directory.

To enable RPC calls you need to pass those program arguments:
--rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18443 --rpcBlockNotificationPort=4159

The default rpcPort for regtest from Bitcoin Core 0.16 and higher is: 18443
The default rpcPort for testnet is: 18332
For mainnet: 8332

If you run 2 clients and want to receive the block notifications on both use different rpcBlockNotificationPorts
5159 and 4159 are defined in the blocknotify file.

For reg test setup with localhost those are typical program arguments:
--baseCryptoNetwork=btc_regtest --useLocalhostForP2P=true --nodePort=3332 --appName=bisq-LRTAli --fullDaoNode=true --rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18443 --rpcBlockNotificationPort=4159


For mainnet:
--fullDaoNode=true --rpcUser=bisq --rpcPassword=bisqPW --rpcPort=8332 --rpcBlockNotificationPort=4159

If you use mainnet it is recommended to use a Bitcoin node with no funds in the wallet to avoid security risks when
enabling RPC or take sufficient precautions from your network setup.

In the bitcoin.conf file you need to set txindex=1.
That causes a re-index of the whole data base which takes considerable time with a
mainnet node.

If you want to dump the blockchain data to json add: --dumpBlockchainData=true (used for BSQ block explorer)

If you use RegTest in development environment you need to create the genesis transaction.
Create one Bitcoin transaction from Bitcoin Core to one or 2 Bisq instances using the BSQ receive addresses from those apps (1 tx with 2 or more outputs to the Bisq app).
If you copy the BSQ address and use that in Bitcoin Core you need to remove the "B" at the beginning. This is only for protection to mix up BTC and BSQ addresses but without the B it is a native Bitcoin address.
Create one block with the debug command line inside Bitcoin Core (generate 1). Look up the block height in the info screen in the debug window.
Set the block height and transaction ID at with options genesisBlockHeight and genesisTxId.
Restart the Bisq apps. After that the app will recognize the received Bitcoin as BSQ.

Here are example options for regtest mode:
--daoActivated=true --genesisBlockHeight=111 --genesisTxId=aa92a8d56be3aaafc6b1a8d248ae67c221d78a31de8867a9564e7ae24340b495 --useDevPrivilegeKeys=true --useDevMode=true--baseCurrencyNetwork=BTC_REGTEST  --useLocalhostForP2P=true --nodePort=3612 --appName=bisq-BTC_REGTEST_Bob_dao --fullDaoNode=true --rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18443 --rpcBlockNotificationPort=5159
