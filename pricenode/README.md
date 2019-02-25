# bisq-pricenode

## Overview

The Bisq pricenode is a simple HTTP service that fetches, transforms and relays data from third-party price providers to Bisq exchange clients on request. Available prices include:

 - Bitcoin exchange rates, available at `/getAllMarketPrices`, and
 - Bitcoin mining fee rates, available at `/getFees`

Pricenodes are deployed in production as Tor hidden services. This is not because the location of these nodes needs to be kept secret, but rather so that Bisq exchange clients do not need to exit the Tor network in order to get price data.

Anyone can run a pricenode, but it must be _discoverable_ in order for it to do any good. For exchange clients to discover your pricenode, its .onion address must be hard-coded in the Bisq exchange client's `ProvidersRepository` class. Alternatively, users can point explicitly to given pricenode (or set of pricenodes) with the exchange client's `--providers` command line option.

Pricenodes can be deployed anywhere Java and Tor binaries can be run. Instructions below cover deployment on localhost, and instructions [how to deploy on Heroku](README-HEROKU.md) are also available.

Pricenodes should be cheap to run with regard to both time and money. The application itself is non-resource intensive and can be run on the low-end of most providers' paid tiers.

A [pricenode operator](https://github.com/bisq-network/roles/issues/5)'s main responsibilities are to ensure their node(s) are available and up-to-date. Releases are currently source-only, with the assumption that most operators will favor Git-based "push to deploy" workflows. To stay up to date with releases, operators can [subscribe to this repository's releases.atom feed](https://github.com/bisq-network/pricenode/releases.atom) and/or get notifications in the `#pricenode` Slack channel.

Operating a production pricenode is a valuable service to the Bisq network, and operators should issue BSQ compensation requests accordingly.


## Prerequisites for running a pricenode

To run a pricenode, you will need:

  - [BitcoinAverage API keys](https://bitcoinaverage.com/en/plans). Free plans are fine for local development or personal nodes; paid plans should be used for well-known production nodes.
  - JDK 8 if you want to build and run a node locally.
  - The `tor` binary (e.g. `brew install tor`) if you want to run a hidden service locally.


## How to deploy locally

### Configure

Export the following properties as environment variables, e.g.:

    $ export BITCOIN_AVG_PUBKEY=[your pubkey]
    $ export BITCOIN_AVG_PRIVKEY=[your privkey]

Or add them to your `bisq.properties` config file, e.g.:

    $ echo BITCOIN_AVG_PUBKEY=[your pubkey] >> $HOME/.config/bisq.properties
    $ echo BITCOIN_AVG_PRIVKEY=[your privkey] >> $HOME/.config/bisq.properties

> TIP: Using the `bisq.properties` config file has the advantage of not needing to specify environment variables in your IDE. Running the app and running tests will "just work" regardless where and how you run them.

### Build

    ./gradlew assemble

### Run

    java -jar ./build/libs/bisq-pricenode.jar [max-blocks] [request-interval-mins]

### Test

To manually test endpoints, run each of the following:

    curl http://localhost:8080/getAllMarketPrices
    curl http://localhost:8080/getFees
    curl http://localhost:8080/getParams
    curl http://localhost:8080/getVersion
    curl http://localhost:8080/info

### Run as Tor hidden service

With your pricenode running at localhost:8080, run:

    tor -f torrc

Wait for the process to report that it is "100% bootstrapped", then copy your newly-generated .onion address:

    export PRICENODE_ONION=$(cat build/tor-hidden-service/hostname)

Test the endpoints of your hidden service via curl with the --socks5-proxy option:

    curl --socks5-hostname 127.0.0.1:9050 http://$PRICENODE_ONION/getAllMarketPrices


## How to deploy elsewhere

 - [README-HEROKU.md](README-HEROKU.md)
 - [docker/README.md](docker/README.md)
