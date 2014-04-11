# bitsquare.io

Bitsquare is a P2P Fiat-BTC Exchange, extensible to a generic P2P trading platform (include commodities and
cryptocurrencies)

This is just a first very basic GUI prototype with mock data.
There is only the trade process for Sell BTC and the role of the offer taker modelled yet.

The project use Java 8 and Maven.

### Implemented (prototype level):
* Screen for orderbook with filtering mock offers by amount, price and order type (buy, sell), other filters not impl. yet
* Screen for creating an offer (needs update)
* Screen for offer taking and payment process
* Simple storage for some filter attributes


### Next steps:
* Other trade variants (Buy BTC taker, Sell BTC offerer, Sell BTC offerer)
* Arbitrator integration
* bitcoinj integration
* messaging system
* ...


### Screenshots of basic screens:
* [Orderbook screen](https://github.com/bitsquare/bitsquare/screenshots/orderbook.png)
* [Trade screen](https://github.com/bitsquare/bitsquare/screenshots/orderbook.png)
* [Bank transfer screen](https://github.com/bitsquare/bitsquare/screenshots/orderbook.png)
* [Trade completed screen](https://github.com/bitsquare/bitsquare/screenshots/orderbook.png)

### Links:
* Web: http://bitsquare.io
* Whitepaper: https://docs.google.com/document/d/1d3EiWZdaM89-P6MVhS53unXv2-pDpSFsN3W4kCGXKgY/edit?pli=1
* Overview: http://bitsquare.io/images/overview.png
* Discussion: https://bitcointalk.org/index.php?topic=462236
