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


### Local Bisq P2P network

For the local P2P network we prefer to use `localhost`, not the Tor network as it is much faster. But if needed you can combine any of the following combinations of Bitcoin network mode and P2P network mode:
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

_Remember to generate a new block in the Bitcoin Core console after taking an offer using the command `generate 1` to trigger a block confirmation._

### Open questions?
If there are any open questions or instructions are not clear, please add a PR for improving that file and/or join us on Slack and get in touch.
