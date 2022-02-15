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

  - JDK 8 if you want to build and run a node locally.
  - The `tor` binary (e.g. `brew install tor`) if you want to run a hidden service locally.

## How to deploy for production

### Install

Run the one-command installer:

```bash
curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/pricenode/install_pricenode_debian.sh | sudo bash
```

At the end of the installer script, it should print your Tor onion hostname.

### Test

To manually test endpoints, run each of the following:

``` bash
curl http://localhost:8080/getAllMarketPrices
curl http://localhost:8080/getFees
curl http://localhost:8080/getParams
curl http://localhost:8080/info
```

### Monitoring

If you run a main pricenode, you also are obliged to activate the monitoring feed by running

```bash
bash <(curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/monitor/install_collectd_debian.sh)
```
Follow the instruction given by the script and report your certificate to the [@bisq-network/monitoring](https://github.com/orgs/bisq-network/teams/monitoring-operators) team.

Furthermore, you are obliged to provide network size data to the monitor by running
```bash
curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/pricenode/install_networksize_debian.sh | sudo bash
```

### Updating

Update your bisq code in /bisq/bisq with ```git pull```

Then build an updated pricenode:
```./gradlew :pricenode:installDist  -x test```

## How to deploy elsewhere

 - [README-HEROKU.md](README-HEROKU.md)
 - [docker/README.md](docker/README.md)


## Bitcoin mining fee estimates

The pricenode exposes a service API to Bisq clients under `/getFees`.

This API returns a mining fee rate estimate, representing an average of several mining fee rate values retrieved from different `mempool.space` instances.

To configure which `mempool.space` instances are queried to calculate this average, see the relevant section in the file `application.properties`.
