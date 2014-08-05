# Bitsquare
![Bitsquare Logo](http://bitsquare.io/images/logo.png)

## About Bitsquare
Bitsquare is a **P2P Fiat-BTC Exchange**.   
It allows to trade fiat money (USD, EURO, ...) for Bitcoins without relying on a centralized exchange like MtGox.  
Instead, all participants form a peer to peer market.

## Dependencies
The project use **Java 8** and **Maven**.  
We use the **BitcoinJ** library and **TomP2P** for DHT and direct messaging.

## Development setup

### Build the 2 master branches of the external libraries:

We use [that fork](https://github.com/bitsquare/TomP2P) from the actual TomP2P master branch.  
You need to check that branch out and deploy it to the local maven repository.  
Build the project with:  
**mvn clean install -DskipTests**  (DskipTests because some unit tests are failing in the master branch)

We use also [that fork](https://github.com/bitsquare/bitcoinj) of the latest BitcoinJ master branch.  
You need to check that branch out as well and deploy it to the local maven repository.  
Build the project with:  
**mvn clean install**


### Regtest mode for local testing  
For local testing it is best to use the **regtest** mode from the Bitcoin QT client.  
You need to edit (or first create inside the bitcoin data directory) the bitcoin.config file and set regtest=1.  

Here are the typical locations for the data directory:

Windows:  
%APPDATA%\Bitcoin\  
(XP) C:\Documents and Settings\username\Application Data\Bitcoin\bitcoin.conf  
(Vista, 7) C:\Users\username\AppData\Roaming\Bitcoin\bitcoin.conf  

Linux:  
$HOME/.bitcoin/  
/home/username/.bitcoin/bitcoin.conf  

Mac OSX:  
$HOME/Library/Application Support/Bitcoin/  
/Users/username/Library/Application Support/Bitcoin/bitcoin.conf  

Take care if you have real bitcoins in your Bitcoin QT wallet (backup and copy first your data directory)!  
More information about bitcoin.conf can be found [here](https://en.bitcoin.it/wiki/Running_Bitcoin).

You can generate coins on demand with the Bitcoin QT client with the following command in the console (under the help menu you find the console window):  
**setgenerate true 101**  
101 is used only for the first start because of the coin maturity of 100 blocks. Later for mining of a single block you can use 1 as number of blocks to be created.

More information about the regtest mode can be found [here](https://bitcoinj.github.io/testing).  

The network mode is defined in the guice module (BitSquareModule) and is default set to regtest.  
Testnet should also work, but was not tested for a while as for developing regtest is much more convenient.  
Please don't use main net with real money, as the software is under heavy development and you can easily lose your funds!


## Resources:
* [Web](http://bitsquare.io)
* [Development mailing list](https://groups.google.com/forum/#!forum/bitsquare)
* [Video (first prototype)](https://www.youtube.com/watch?v=ByfnzJzi0bo)
* [White paper](https://docs.google.com/document/d/1d3EiWZdaM89-P6MVhS53unXv2-pDpSFsN3W4kCGXKgY/edit)


### Screenshots of the basic use cases (first prototype):
* [Orderbook screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook1.png)
* [Orderbook screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook2.png)
* [Create Offer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/create_offer_2.png)
* [Take offer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/take_offer.png)
* [Deposit tx screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/deposit_conf.png)
* [Check bank tx screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/bank_tx_inited.png)
* [Trade completed screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/trade_complete.png)


### Transactions of a trade on main net:
* [Offerer registration tx](https://blockchain.info/de/tx/06ea3c2a5fb79f622d3e3def7c6a20274274fcbf9ec69b95bdfe9b347bbbdf76)
* [Taker registration tx](https://blockchain.info/tx/8352ab9fe78593f48ef70d414d494ebd614d99fab147d0342910525e9284ba8f)
* [Create offer fee tx](https://blockchain.info/tx/24f4d229edace44d9123628363a16cd7041f5d34ba6bef812807b9be03a64692)
* [Take offer fee tx](https://blockchain.info/tx/06ea3c2a5fb79f622d3e3def7c6a20274274fcbf9ec69b95bdfe9b347bbbdf76)
* [Deposit tx](https://blockchain.info/de/tx/98c6ae55963022871216a6a124c1e1ed7f6308560e76b72617b6b54cf50ef412)
* [Payout tx](https://blockchain.info/tx/498e2c299ca991b27f61b63fb6ee457819ee9e33ee5a1d250fde47eb15199adc)
