# Bisq dev setup guide

This guide describes how to set up a complete Bisq development environment running against a local Bitcoin regtest network. You'll run your own Bisq seed node, arbitration and trading instances in order to allow for end-to-end development and testing.


## Prerequisites

Follow the instructions in [build.md](build.md) to build Bisq from source, and it is also recommended to follow the instructions in [idea-import.md](idea-import.md) to be able to run everything from within IntelliJ IDEA. Please also read and follow the instructions in [CONTRIBUTING.md](../CONTRIBUTING.md) prior to submitting any pull requests.


## Overview

When developing Bisq, you usually want to use Bitcoin in **regtest** mode and do all networking on **localhost** instead of using the Tor network. Typically, you'll want an environment set up with the following components:

 - Bitcoin Core or bitcoind in regtest mode
 - A local Bisq seed node
 - A local Bisq arbitrator & mediator instance
 - 2 local Bisq trading instances (BTC buyer and BTC seller for executing a trade)

You'll set up each of these in the steps that follow.


## Run Bitcoin Core (or bitcoind) in regtest mode

The regtest mode operates a local Bitcoin network on your computer. This environment is ideally suited for testing because you are able to create blocks on demand (no need to wait for confirmations) and you don't need to download the blockchain. By creating blocks you act like a miner and you can generate new Bitcoin.

You can find more information about the Bitcoin regtest mode [here](https://bitcoin.org/en/developer-examples#regtest-mode).

Navigate to the [bitcoin.conf](https://en.bitcoin.it/wiki/Running_Bitcoin#Bitcoin.conf_Configuration_File) file and set `regtest=1` and `peerbloomfilters=1`, or add `-regtest -peerbloomfilters=1` as a program arguments when starting Bitcoin Core.

At first startup you need to create 101 blocks using the command `generatetoaddress 101 address`* from the terminal inside Bitcoin Core, where `address` value could be obtained with the command `getnewaddress`. 101 blocks are required because of the coin maturity (100 blocks) so you need one more to have at least 50 BTC available for spending.

Example:

    generatetoaddress 101 bcrt1qhqn0t94uc269szakr4ez0zh7erdd6tlm4pv6mg

Later you can create new blocks with `generatetoaddress 1 address`*.

*If you are using Bitcoin Core v.0.18 or less, use instead `generate 1`.

## Understand Bisq P2P network options

For the local P2P network we prefer to use `localhost`, not the Tor network as it is much faster. But if needed you can combine any of the following combinations of Bitcoin network mode and P2P network mode:

 - localhost + regtest
 - localhost + testnet
 - localhost + mainnet
 - Tor + regtest
 - Tor + testnet
 - Tor + mainnet


## Understand Bisq program arguments

There are several program arguments required to run in development mode.

Here is an overview:

 - `--baseCurrencyNetwork`: The BTC network to use. Possible values are: `BTC_REGTEST`, `BTC_TESTNET`, `BTC_MAINNET` (default)
 - `--useLocalhostForP2P`: Uses localhost instead of Tor for Bisq P2P network
 - `--nodePort`: Port number for localhost mode. For seed nodes there is a convention with the last digit is marking the network type and there is a list of hard coded seed nodes addresses (see: `DefaultSeedNodeAddresses.java`). For regtest: 2002 and 3002. For testnet 2001, 3001 and 4001 and for mainnet:  2000, 3000 and 4000. For normal nodes the port can be chosen freely.
 - `--useDevPrivilegeKeys`: Important for dev testing to allow the developer key for arbitration registration
 - `--appName`: Custom application name which is used for the data directory. It is important to separate your nodes to not interfere. If not set, it uses the default `Bisq` directory.


## Run Bisq seednode

For localhost/regtest mode run the `SeedNodeMain` class or `./bisq-seednode` script in the root project dir with following program arguments:

    --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=2002 --appName=bisq-BTC_REGTEST_Seed_2002


### Run Bisq arbitrator/mediator instance

For localhost/regtest mode run the `BisqAppMain` class or `./bisq-desktop` script in the root project dir with following program arguments:

    --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=4444 --appName=bisq-BTC_REGTEST_arbitrator

Once it has started up go to `Account` and click `CMD +n`. This will open a new tab for `Arbitration registration`. Select the tab and you will see a popup with a pre-filled private key. That is the developer private key (which is only valid if `--useDevPrivilegeKeys` is set) which allows you to register a new arbitrator. Follow the next screen and complete registration.
Next you have to register a mediator as well. Click `CMD + d`. This will open a new tab for `Mediator registration`. Follow the same steps as for the arbitrator registration before. Registration of legacy arbitrators was done with `CMD +n`. It is not needed anymore so we refer with the term arbitrator to the new arbitrator (or refund agent).

_Note: You need only register once but if you have shut down all nodes (including seed node) you need to start up the arbitrator again after you start the seed node so the arbitrator re-publishes his data to the P2P network. After it has started up you can close it again. You cannot trade without having an arbitrator available._


### Run two Bisq trade instances

For localhost/regtest mode run the `BisqAppMain` class or `./bisq-desktop` script in the root project dir with following program arguments:

    --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=5555 --appName=bisq-BTC_REGTEST_Alice

and

    --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=6666 --appName=bisq-BTC_REGTEST_Bob

At this point you can now perform trades between Alice and Bob using your local regtest environment and test from both the buyer's and seller's perspective. You can also open disputes with `cmd+o` and see how the arbitration system works (run the arbitrator in that case as well).

_Remember to generate a new block in the Bitcoin Core console after taking an offer using the command `generatetoaddress 1 address` to trigger a block confirmation._
