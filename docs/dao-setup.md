# Bisq DAO development setup guide

This guide describes how to setup a Bisq DAO development environment running against a local Bitcoin regtest network.
It assumes you have already configured your development environment following the [dev setup guide](dev-setup.md).

## Configure Bitcoin Core

1. Create/configure the [bitcoin.conf](https://en.bitcoin.it/wiki/Running_Bitcoin#Bitcoin.conf_Configuration_File) file inside the Bitcoin Core [data directory](https://en.bitcoin.it/wiki/Data_directory#Default_Location) as follows.

_Please note that `txindex` triggers a resync of the entire blockchain and be aware if you set that on mainnet it will take a while. Also, extra settings for more security are recommended if you run this in mainnet mode._

Linux:
```
regtest=1

# The default rpcPort for regtest from Bitcoin Core 0.16 and higher is: 18443
# The default rpcPort for testnet is: 18332
# For mainnet: 8332
[regtest]
peerbloomfilters=1
rpcport=18443

server=1
txindex=1
rpcuser=YOUR_USER_NAME
rpcpassword=YOUR_PW
blocknotify=bash ~/.bitcoin/blocknotify %s
```

MacOS:
```
regtest=1

# The default rpcPort for regtest from Bitcoin Core 0.16 and higher is: 18443
# The default rpcPort for testnet is: 18332
# For mainnet: 8332
[regtest]
peerbloomfilters=1
rpcport=18443

server=1
txindex=1
rpcuser=YOUR_USER_NAME
rpcpassword=YOUR_PW
blocknotify=bash ~/Library/Application\ Support/Bitcoin/blocknotify %s
```

Windows:
```
regtest=1

# The default rpcPort for regtest from Bitcoin Core 0.16 and higher is: 18443
# The default rpcPort for testnet is: 18332
# For mainnet: 8332
[regtest]
peerbloomfilters=1
rpcport=18443

server=1
txindex=1
rpcuser=YOUR_USER_NAME
rpcpassword=YOUR_PW
blocknotify="%AppData%\Bitcoin\blocknotify.bat" %s
```

2. Create a `blocknotify` file (`blocknotify.bat` on Windows) inside the Bitcoin Core [data directory](https://en.bitcoin.it/wiki/Data_directory#Default_Location) with the following content.
This defines the ports where a new block event gets forwarded. Bisq will listen on that port and each Bisq node needs to use a different port. You can add or remove ports as needed.

_On Windows, you will need to download and install [ncat](https://nmap.org/ncat/) to be able to use the ncat command._

Linux/MacOS:
```bash
#!/bin/bash
echo $1 | nc -w 1 127.0.0.1 5120
echo $1 | nc -w 1 127.0.0.1 5121
echo $1 | nc -w 1 127.0.0.1 5122
echo $1 | nc -w 1 127.0.0.1 5123
```

Windows:
```batch
echo %1 | ncat -w 1 127.0.0.1 5120
echo %1 | ncat -w 1 127.0.0.1 5121
echo %1 | ncat -w 1 127.0.0.1 5122
echo %1 | ncat -w 1 127.0.0.1 5123
```


## Program arguments for DAO mode

 - `--daoActivated`: If set to true it enables the DAO mode. For testnet and regtest it is enabled by default.
 - `--genesisBlockHeight`: If set it overrides the hard coded block height of the genesis tx. Set it to your local genesis tx height.
 - `--genesisTxId`: If set it overrides the hard coded genesis tx ID. Set it to your local genesis tx ID.
 - `--fullDaoNode`: If true it enables full DAO node mode (in contrast to default lite node mode). At least one seed node must be running as a full DAO node to support other lite nodes.
 - `--rpcUser`: RPC user as defined in bitcoin.conf
 - `--rpcPassword`: RPC pw as defined in bitcoin.conf
 - `--rpcPort`: RPC port. For regtest 18443
 - `--rpcBlockNotificationPort`: One of the ports defined in the `blocknotify` file.


## Run the application in DAO mode

If you want to run any instance in DAO mode, use the following program arguments.

Full node mode:

`--daoActivated=true --genesisBlockHeight=111 --genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf --baseCurrencyNetwork=BTC_REGTEST --useDevPrivilegeKeys=true --useLocalhostForP2P=true --nodePort=7777 --appName=bisq-BTC_REGTEST_Alice_dao --fullDaoNode=true --rpcUser=YOUR_USER_NAME --rpcPassword=YOUR_PW --rpcPort=18443 --rpcBlockNotificationPort=5120`

Lite node mode:

Note: At least one seed node must be running as a full DAO node to support other lite nodes.

`--daoActivated=true --genesisBlockHeight=111 --genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf --baseCurrencyNetwork=BTC_REGTEST --useDevPrivilegeKeys=true --useLocalhostForP2P=true --nodePort=8888 --appName=bisq-BTC_REGTEST_Bob_dao`

_Don't forget to use different rpcBlockNotificationPorts for different full node instances, otherwise only one node will receive the new block event forwarded to that port._


## DAO genesis transaction

### Use the predefined setup

The creation of the genesis tx is a bit cumbersome. To make it easier to get started you can use the [dao-setup.zip](dao-setup.zip) file.
Extract the file and use those data directories for the Bitcoin Core as well as the Alice and Bob instances which are configured to have the genesis tx as defined in the above program arguments (`30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf` at height `111`).

_You need to adjust the path to the `blocknotify` file inside of `bitcoin.conf` before starting Bitcoin Core._

### Setup a custom DAO genesis transaction

To create your own genesis transaction follow these steps:

 - Send 2.50010000 BTC from Bitcoin Core to another address inside Bitcoin Core (label it with `Genesis funding address`).
 - Go to the send screen and open the coin control feature. Select the labeled transaction output of the address labeled with `Genesis funding address`. Use that as the only input source for the genesis tx.
 - Start Alice in full or lite node mode and go to the DAO/Wallet/Receive screen. Copy the BSQ address and use it for one of the receivers of the genesis tx. When pasting into Bitcoin Core remove the `B` prefix - that prefix is marking a BSQ address but technically it is a BTC address.
 - Do the same with Bob.
 - You send in sum exactly 2.5 BTC to both Alice and Bob. You can choose how to distribute it (e.g. 1 BTC to Alice 1.5 BTC to Bob).
 - Set the miner fee so that it is exactly the remaining 0.00010000 BTC. That might be the tricky part as miner fee selection is not very convenient in Bitcoin Core. In worst case if you cannot get the right miner fee you can add the difference to one of the receivers (e.g. send 1.0000234 BTC instead of 1 BTC).

_Note: It is important that there is exactly 2.5 BTC spent entirely as described, otherwise the genesis tx is invalid._
