# bitsquare.io

Bitsquare is a P2P Fiat-BTC Exchange, extensible to a generic P2P trading platform (include commodities and
cryptocurrencies)

This is just a first very basic GUI prototype with mock data.
There is only the trade process for Sell BTC and the role of the offer taker modelled yet.

The project use Java 8 and Maven.

We use bitcoinj library as a submodule. To get the project with the submodule included use:
git clone --recursive git://github.com/bitsquare/bitsquare

### Implemented (prototype level):
* Screen for orderbook with filtering mock offers by amount, price and order type (buy, sell)
* Screen for creating an offer
* Screen for offer taking and payment process (needs update)
* Simple persistence
* bitcoinj integration
* Setup with account registration and tx with OP_RETURN + embedded and blinded bank account data
* Offer fee payment with a OP_RETURN tx and fees to miners

### Next steps:
* Other trade variants (Buy BTC taker, Sell BTC offerer, Sell BTC offerer)
* Arbitrator integration
* Messaging system
* ...


### Screenshots of basic screens:
* [Registration screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/reg1.png)
* [Registration screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/reg2.png)
* [Registration screen 3](https://github.com/bitsquare/bitsquare/tree/master/screenshots/reg3.png)
* [Orderbook screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook1.png)
* [Orderbook screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook2.png)
* [Orderbook screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/orderbook.png)
* [Create Offer screen 1](https://github.com/bitsquare/bitsquare/tree/master/screenshots/newOffer1.png)
* [Create Offer screen 2](https://github.com/bitsquare/bitsquare/tree/master/screenshots/newOffer2.png)
* [Trade screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/trade.png)
* [Bank transfer screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/bank_transfer.png)
* [Trade completed screen](https://github.com/bitsquare/bitsquare/tree/master/screenshots/completed.png)


### Links:
* Web: http://bitsquare.io
* Whitepaper: https://docs.google.com/document/d/1d3EiWZdaM89-P6MVhS53unXv2-pDpSFsN3W4kCGXKgY/edit?pli=1
* Overview: http://bitsquare.io/images/overview.png
* Discussion: https://bitcointalk.org/index.php?topic=462236
