## Bisq developer guide

This guide describes the development setup for developers who want to contribute to Bisq.

### Getting started

Please follow the instructions in the [README](https://github.com/bisq-network/bisq/tree/master/README.md) file for running Bisq from source code. Be sure to have the right JDK version installed. Best to use IntelliJ IDEA (there is a free community edition). Please read also the [CONTRIBUTING](https://github.com/bisq-network/bisq/tree/master/doc/CONTRIBUTING.md) file.

### Basic setup

For development you usually want to use **regtest** mode with **localhost** (instead using the Tor network). 

You want to run typically a setup with those components:
- Bitcoin Core or bitcoind in regtest mode
- A local Bisq seed node
- A local Bisq arbitrator
- 2 Bisq trading peers (BTC buyer and BTC seller for executing a trade)

### Bitcoin Core (or bitcoind) in regtest mode

The regtest mode operates a local Bitcoin network on your computer. This environment is ideally suited for testing because you are able to create blocks on demand (no need to wait for confirmations) and you don't need to download the blockchain. By creating blocks you act like a miner and you can generate new Bitcoin.
You can find more information about the Bitcoin regtest mode [here](https://bitcoin.org/en/developer-examples#regtest-mode).

Navigate to the [bitcoin.conf](https://en.bitcoin.it/wiki/Running_Bitcoin#Bitcoin.conf_Configuration_File) file and set `regtest=1`, or add `-regtest` as a program argument when starting Bitcoin Core.

At first startup you need to create 101 blocks using the command `generate 101` from the terminal inside Bitcoin Core. 101 blocks are required because of the coin maturity (100 blocks) so you need one more to have at least 50 BTC available for spending.
Later you can create a new blocks with `generate 1`.


#### Optional for DAO mode

If you want to run Bisq with DAO mode enabled you need to configure the `bitcoin.conf` file inside the Bitcoin Core data directory [1] as well to add the `blocknotify` file.

**bitcoin.conf:**
```
regtest=1

# The default rpcPort for regtest from Bitcoin Core 0.16 and higher is: 18443
# The default rpcPort for testnet is: 18332
# For mainnet: 8332
rpcport=18443  

server=1
txindex=1
rpcuser=YOUR_USER_NAME
rpcpassword=YOUR_PW
blocknotify=bash [PATH TO DATA DIR]/blocknotify %s
```

_Please note that `txindex` triggers a resync of the entire blockchain (be aware if you set that on mainnet as that it will take a while)_

The `blocknotify` file need to be added to the Bitcoin Core data directory as well:
```
#!/bin/bash
echo $1 | nc -w 1 127.0.0.1 5120
echo $1 | nc -w 1 127.0.0.1 5121
echo $1 | nc -w 1 127.0.0.1 5122
echo $1 | nc -w 1 127.0.0.1 5123
```

It defines the ports where a new block event gets forwarded. Bisq will listen on that port and each Bisq node need to use a different port. You can add or remove port from the list inside the file if needed.


### Local Bisq P2P network

For the local P2P network we prefer to use `localhost` not the Tor network as it is much faster. But if needed you can combine any of the following combinations of Bitcoin network mode and P2P network mode:
- localhost + regtest
- localhost + testnet
- localhost + mainnet
- Tor + regtest
- Tor + testnet
- Tor + mainnet

### Program arguments

There are several program arguments required to run in development mode. 

Here is an overview:

- --baseCurrencyNetwork: The BTC network to use. Possible values are: BTC_REGTEST, BTC_TESTNET, BTC_MAINNET (default)
- --useLocalhostForP2P: Uses localhost instead of Tor for Bisq P2P network
- --nodePort: Port number for localhost mode. For seed nodes there is a convention with the last digit is marking the network type and there is a list of hard coded seed nodes addresses (see: DefaultSeedNodeAddresses.java). For regtest: 2002 and 3002. For testnet 2001, 3001 and 4001 and for mainnet:  2000, 3000 and 4000. For normal nodes the port can be chosen freely.
- --myAddress: Needed for seed nodes only (e.g.: `localhost:3002`) 
- --useDevPrivilegeKeys: Important for dev testing to allow the developer key for arbitration registration
- --appName: Custom application name which is used for the data directory. It is important to separate your nodes to not interfere... If not set is uses the default `Bisq` directory.

#### Program arguments for DAO mode

--daoActivated: If set to true it enables the DAO mode. For testnet and regtest it is enabled by default.  
--genesisBlockHeight: If set it overrides the hard coded block height of the genesis tx. Set it to your local genesis tx height.
--genesisTxId: If set it overrides the hard coded genesis tx ID. Set it to your local genesis tx ID.
--fullDaoNode: If true it enabled full DAO node mode (in contrast to default lite node mode). At least one seed node must be running as a full DAO node to support other lite nodes.
--rpcUser: RPC user as defined in bitcoin.conf 
--rpcPassword: RPC pw as defined in bitcoin.conf
--rpcPort: RPC port. For regtest 18443 
--rpcBlockNotificationPort: One of the ports defined in the `blocknotify` file inside the Bitcoin data directory (see: DAO setup for Bitcoin Core).

### Bisq seednode

For localhost/regtest mode run the SeedNodeMain.java class or the seednode.jar (inside the seednode/build/libs folder) with following program arguments:

`--baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=2002 --myAddress=localhost:2002 --appName=bisq-BTC_REGTEST_Seed_2002`

### Bisq arbitrator instance
For localhost/regtest mode run the BisqAppMain.java class or the desktop.jar (inside the desktop/build/libs folder) with following program arguments:

`--baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=4444 --appName=bisq-BTC_REGTEST_arbitrator`

Once it has started up go to Account and click `cmd +r`. This will open a new tab for `Arbitration registration`. Select the tab and you will see a popup with a pre-filled private key. That is the developer private key (which is only valid if useDevPrivilegeKeys is set) which allows you to register a new arbitrator. Follow the next screen and complete registration.

_Note: You need only register once but if you have shut down all nodes (including seed node) you need to start up the arbitrator again after you start the seed node so the arbitrator re-publishes his data to the P2P network. After it has started up you can close it again. You cannot trade without having an arbitrator available._

### Bisq trade instances

For localhost/regtest mode run the BisqAppMain.java class or the desktop.jar (inside the desktop/build/libs folder) with following program arguments:

`--baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=5555 --appName=bisq-BTC_REGTEST_Alice`
and
`--baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=6666 --appName=bisq-BTC_REGTEST_Bob`

At this point you can now perform trades between Alice and Bob using your local regtest environment and test from both the buyer's and seller's perspective. You can also open disputes with `cmd+o` and see how the arbitration system works (run the arbitrator in that case as well).

_Note, remember to generate a new block in the Bitcoin Core console after taking an offer using the command `generate 1` to trigger a block confirmation._

### DAO mode
If you want to run any instance in DAO mode use those program arguments:

Full node mode:

`--daoActivated=true --genesisBlockHeight=111 --genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf --baseCurrencyNetwork=BTC_REGTEST --useDevPrivilegeKeys=true --useLocalhostForP2P=true --nodePort=7777 --appName=bisq-BTC_REGTEST_Alice_dao --fullDaoNode=true --rpcUser=YOUR_USER_NAME --rpcPassword=YOUR_PW --rpcPort=18443 --rpcBlockNotificationPort=5120`

Lite node mode:

`--daoActivated=true --genesisBlockHeight=111 --genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf --baseCurrencyNetwork=BTC_REGTEST --useDevPrivilegeKeys=true --useLocalhostForP2P=true --nodePort=8888 --appName=bisq-BTC_REGTEST_Bob_dao`

_Note, don't forget to use different rpcBlockNotificationPorts for different full node instances, otherwise only one node will receive the new block event forwarded to that port._

### DAO genesis transaction

#### Use the predefined setup

The creation of the genesis tx is a bit cumbersome. To make it easier to get started you can use the `Bisq_DAO_regtest_setup.zip` file which you find here in the same directory.
Extract the `Bisq_DAO_regtest_setup.zip` file and use those data directories for the Bitcoin Core as well as the Alice and Bob instances which are configured to have the genesis tx as defined in the above program arguments (`30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf` at height `111`).

_You need to adjust the path to the `blocknotify` file inside of `bitcoin.conf` before starting Bitcoin Core._

#### Setup a custom DAO genesis transaction

To create your own genesis transaction follow those steps:

- Send 2.50010000 BTC from Bitcoin Core to another address inside Bitcoin Core (label it with `Genesis funding address`).
- Go to the send screen and open the coin control feature. Select the labeled transaction output of the address labeled with `Genesis funding address`. Use that as the only input source for the genesis tx.
- Start Alice in full or lite node mode and go to the DAO/Wallet/Receive screen. Copy the BSQ address and use it for one of the receivers of the genesis tx. When pasting into Bitcoiin Core remove the `B` prefix - that prefix is marking a BSQ address but technically it is a BTC address.
- Do the same with Bob.
- You send in sum exactly 2.5 BTC to both Alice and Bob. You can choose how to distribute it (e.g. 1 BTC to Alice 1.5 BTC to Bob).
- Set the miner fee so that it is exactly the remaining 0.00010000 BTC. That might be the tricky part as miner fee selection is not very convenient in Bitcoin Core. In worst case if you cannot get the right miner fee you can add the difference to one of the receivers (e.g. send 1.0000234 BTC instead of 1 BTC).

_Note: It is important that there is exactly 2.5 BTC spent entirely as described, otherwise the genesis tx is invalid._

### Compensation for your contributions
Bisq is not a company but operates as a [DAO](https://docs.bisq.network/dao/phase-zero.html). For any development work merged into the Bisq master branch you can file a [compensation request](https://github.com/bisq-network/compensation) and earn BSQ (the DAO native token). Learn more about the Bisq DAO at our [docs](https://docs.bisq.network/dao/phase-zero.html) pages.

### Open questions?
If there are any open questions or instructions are not clear, please add a PR for improving that file and/or join us on Slack and get in touch.


[1] Data directory

You typically find the data directories here:  
OSX: `/Users/username/Library/Application Support/bisq/`  
Linux: `/home/username/.bisq/`    
Windows XP: `C:\Documents and Settings\username\Application Data\bisq\`    
Windows Vista or 7: `%appdata%/bisq/`  
