# bitsquare.io

Bitsquare is a P2P Fiat-BTC Exchange, extensible to a generic P2P trading platform (include commodities and
cryptocurrencies)

This is just a proof of concept prototype for demonstrating the basic workflow of the trader process.
It is not at all production code style (no tests, verifications missing, very limited use cases,...).

The project use Java 8 and Maven.
We use the bitcoinj library and TomP2P for DHT and messaging.

### Implemented (prototype level):
* Orderbook with filtering offers by amount, price, order type, trading account(buy, sell)
* Create offer
* Take offer
* Simple persistence
* bitcoinj integration
* Setup with account registration and tx with OP_RETURN + embedded and blinded bank account data
* Offer fee payment with a OP_RETURN tx and fees to miners
* Pay in to MS fund
* Payout from MS fund
* TomP2P as messaging lib integrated and basic use cases in msg screen implemented: orderbook, add order, remove order, peer interaction
* Payment process implemented with messaging for Offerer buy BTC case
* Hash of contract data embedded into tx (OP_RETURN)

### Next steps:
* Conceptual refinements
* Arbitrator integration concept
* Other trade variants (Buy BTC taker, Sell BTC offerer, Sell BTC offerer)
* Develop funding model
* Start development of production version


### Screenshots of basic screens:
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


### Links:
* Web: http://bitsquare.io
* Whitepaper: https://docs.google.com/document/d/1d3EiWZdaM89-P6MVhS53unXv2-pDpSFsN3W4kCGXKgY/edit?pli=1
* Overview: http://bitsquare.io/images/overview.png
* Discussion: https://bitcointalk.org/index.php?topic=462236
