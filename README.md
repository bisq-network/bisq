# bitsquare.io

Bitsquare is a P2P Fiat-BTC Exchange.

The project use Java 8 and Maven.
We use the bitcoinj library and TomP2P for DHT and direct messaging.

For local testing it is best to use the regtest mode from Bitcoin qt clients.
If you want to use the RegTest mode you need to set regtest=1 in the bitcoin.config file inside the bitcoin data directory (https://en.bitcoin.it/wiki/Running_Bitcoin).
Then you can generate coins on demand with the Bitcoin qt client with that command in the console: setgenerate true 101  (101 only for the first start because the coin maturity of 100 blocks).
More information about how to use regtest mode can be found here: https://bitcoinj.github.io/testing
Take care if you have real bitcoin in your Bitcoin qt wallet (backup and copy first your data directory).
You can change the network mode in the guice module: BitSquareModule.java
Testnet should also work, but was not tested a while now as for developing regtest is much more convenient.
Please don't use main net with real money, as the software is under heavy development and you can easily lose your funds.

We use a fork of the actual TomP2P master branch: https://github.com/bitsquare/TomP2P
You need to check that out as well and deploy it to the local maven repository:
mvn clean install -DskipTests


### Resources:
* Web: http://bitsquare.io


### Screenshots of basic the use cases:
* [Registration screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/registration_3.png)
* [Registration screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/registration_bank_account.png)
* [Orderbook screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook1.png)
* [Orderbook screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook2.png)
* [Create Offer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/create_offer_2.png)
* [Take offer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/take_offer.png)
* [Deposit tx screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/deposit_conf.png)
* [Check bank tx screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/bank_tx_inited.png)
* [Trade completed screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/trade_complete.png)
* [More screenshots](https://github.com/bitsquare/bitsquare/tree/master/screenshots)


### Transactions of a test trade on main net:
* [Offerer registration tx](https://blockchain.info/de/tx/06ea3c2a5fb79f622d3e3def7c6a20274274fcbf9ec69b95bdfe9b347bbbdf76)
* [Taker registration tx](https://blockchain.info/tx/8352ab9fe78593f48ef70d414d494ebd614d99fab147d0342910525e9284ba8f)
* [Create offer fee tx](https://blockchain.info/tx/24f4d229edace44d9123628363a16cd7041f5d34ba6bef812807b9be03a64692)
* [Take offer fee tx](https://blockchain.info/tx/06ea3c2a5fb79f622d3e3def7c6a20274274fcbf9ec69b95bdfe9b347bbbdf76)
* [Deposit tx](https://blockchain.info/de/tx/98c6ae55963022871216a6a124c1e1ed7f6308560e76b72617b6b54cf50ef412)
* [Payout tx](https://blockchain.info/tx/498e2c299ca991b27f61b63fb6ee457819ee9e33ee5a1d250fde47eb15199adc)
