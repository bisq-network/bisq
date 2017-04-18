Setup or RPC calls to Bitcoin Core
====================

You need to setup the bitcoin.conf and add a blocknotify sh file. See the examples in the rec_regtest directory.

To enable RPC calls you need to pass those program arguments:
--rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18332 --rpcBlockNotificationPort=4159

If you run 2 clients and want to receive the block notifications on both use different rpcBlockNotificationPorts
5159 and 4159 are defined in the blocknotify file.

For reg test setup with localhost those are typical program arguments:
--bitcoinNetwork=regtest --useLocalhostForP2P=true --nodePort=3332 --appName=bisq-LRTAli --rpcUser=bisq --rpcPassword=bisqPW --rpcPort=18332 --rpcBlockNotificationPort=4159

If you use mainnet it is recommended to use a node with no funds in the wallet to avoid security risks when 
enabling rpc or take sufficient precautions from from network setup.

txindex=1 need to be set. That causes a re-index of the whole data base which takes considerable time with a 
mainnet node.
