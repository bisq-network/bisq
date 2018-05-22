[![Build Status](https://travis-ci.org/mrosseel/bisq-api.svg?branch=master)](https://travis-ci.org/mrosseel/bisq-api)

# Bisq API

**Based on proposal: [Bisq-IP 1](https://github.com/mrosseel/bisq-proposals/blob/api-proposal/http-api.adoc)**

This repository is an add-on to the [bisq-exchange](https://github.com/bisq-network/exchange).

The goal is to add Trading API functionality to Bisq, see [this video](https://www.youtube.com/watch?v=SkPT8bLOYtE&feature=youtu.be) to
see it in action (pretty outdated).


## Prerequisites

* Java 8
* Maven 3.5+


## Bisq configuration

Starting this api is like starting a normal Bisq client: configurations are
read and written to the same location on your disk.

This implies that if you already have a fully configured Bisq application,
the api will use those pre-existing configurations

**Note**: running 2 Bisq instances on one computer (e.g one with API, one without)
is not advisable. By default they both write to the same configuration location.
Changing the appname can fix this issue.


## Compiling the API

This step needs to be done before you can start the API with UI or headless:

    mvn clean install


## Getting started: with UI

The following command will start a GUI Bisq instance on
Bitcoin mainnet (meaning you can lose real BTC):

    mvn compile exec:java -Dexec.mainClass="network.bisq.api.app.BisqApiWithUIMain"


## Getting started: headless

The following command will start a headless Bisq instance on
Bitcoin mainnet (meaning you can lose real BTC):

    mvn compile exec:java -Dexec.mainClass="network.bisq.api.app.ApiMain"


## Developing

When testing it is advisable to run Bisq in REGTEST mode.
See below on how to pass Bisq arguments to enable REGTEST mode.
All regular Bisq arguments can be used.

    mvn compile exec:java \
        -Dexec.mainClass="network.bisq.api.app.BisqApiWithUIMain" \
        -Dexec.args="--baseCurrencyNetwork=BTC_REGTEST --bitcoinRegtestHost localhost --nodePort 2003 --seedNodes=localhost:2225 --useLocalhost true --appName Bisq-Regtest-Bob"


## Exploring the HTTP API

locally generated swagger docs are at:
    http://localhost:8080/swagger

publically generated swagger docs are at:
    https://mrosseel.github.io/bisq-api-examples/

Locally generated **swagger.json** can be found at:
    http://localhost:8080/swagger.json

the admin interface can be found at:
    http://localhost:8080/admin


## Overriding http port and host

Set the environment variable `BISQ_API_PORT` to your desired port.
Set the environment variable `BISQ_API_HOST` to your desired host.
You might also pass program args: `apiPort` and `apiHost`.


    mvn compile exec:java \
        -Dexec.mainClass="network.bisq.api.app.BisqApiWithUIMain" \
        -Dexec.args="--apiPort=8000 --apiHost=localhost"


## Docker for production

Since there is no security implemented yet, please be cautious. We do not consider this API to be production ready yet.
But if you want to give it a try using docker, here is the image with headless version:

    docker run -it -p 8080:8080 -p 9999:9999 bisq/api

or if you want to keep Bisq data outside of docker:

    docker run -it -v ~/.local/share/Bisq:/root/.local/share/Bisq -p 8080:8080 -p 9999:9999 bisq/api

If you want to build your own production image instead of pulling it from docker hub:

    docker build . -f docker/prod/Dockerfile -t bisq/api


## Docker for developers

Since maven dependencies are being fetched after container is started you can seed 'm2' volume used for caching local maven repo:

    docker volume create m2
    docker container create -v m2:/m2 --name m2helperContainer busybox
    docker cp ~/.m2/repository m2helperContainer:/m2/
    docker rm m2helperContainer

Build bisq-api image:

    docker-compose build

Now depending on what scenario you want to run execute one of following commands:

    docker-compose up alice
    docker-compose up alice bob
    docker-compose up alice bob arbitrator

Api ports:

* localhost:8080 Alice
* localhost:8081 Bob
* localhost:8082 Arbitrator

## Integration tests

Integration tests are being run using Arquillian Cube.
Arquillian will create and start containers for alice, bob, arbitrator, seednode and bitcoind on the fly during tests.
You have to build bisq-api image before running integration tests.

Run integration tests:

    docker-compose build #just make sure our images are up to date
    mvn verify -P integration


## Build

In order to build shaded jar:

    mvn package -P shade


## Api naming guidelines:

* resource names should be in plural
* use hyphen-case for multi word resource names (camelCase for everything else: payload properties or query params)
* if you need to return array, use {results,total} pattern in case pagination is required in future
* use GET / to search
* use POST / to create
* use PUT /{id} to update by id
* use DELETE /{id} to remove by id
* use POST /{id}/{action} to perform some action on specific resource


## TODO

* produce jar with all dependencies for easier deployment
