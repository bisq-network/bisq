Setup or RPC calls to Bitcoin Core
====================

You need to setup the bitcoin.conf and add a blocknotify sh file. See the examples in the rec_regtest directory.

To enable RPC calls you need to pass those program arguments:
--rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18332 --rpcBlockNotificationPort=4159

The default rpcPort for regtest and testnet is: 18332
For mainnet: 8332

If you run 2 clients and want to receive the block notifications on both use different rpcBlockNotificationPorts
5159 and 4159 are defined in the blocknotify file.

For reg test setup with localhost those are typical program arguments:
--baseCryptoNetwork=btc_regtest --useLocalhostForP2P=true --nodePort=3332 --appName=bisq-LRTAli --fullDaoNode=true --rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18332 --rpcBlockNotificationPort=4159


For mainnet:
--fullDaoNode=true --rpcUser=bisq --rpcPassword=bisqPW --rpcPort=8332 --rpcBlockNotificationPort=4159

If you use mainnet it is recommended to use a Bitcoin node with no funds in the wallet to avoid security risks when 
enabling rpc or take sufficient precautions from your network setup.

In the bitcoin.conf file you need to set txindex=1. 
That causes a re-index of the whole data base which takes considerable time with a 
mainnet node.

If you want to dumpt the blockchain data to json add: --dumpBlockchainData=true (used for BSQ block explorer)

