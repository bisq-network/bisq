[![Build Status](https://travis-ci.org/mrosseel/bisq-api.svg?branch=master)](https://travis-ci.org/mrosseel/bisq-api)

*WARNING: the current version of the API is configured to use Dev Privilege keys.
If you want to use this on mainnet, you need to change the dependency to a release version of Bisq.*

# Bisq API

**Based on proposal: [Bisq-IP 1](https://github.com/mrosseel/bisq-proposals/blob/api-proposal/http-api.adoc)**

This repository is an add-on to the [bisq-exchange](https://github.com/bisq-network/exchange).

The goal is to add Trading API functionality to Bisq, see [this video](https://www.youtube.com/watch?v=SkPT8bLOYtE&feature=youtu.be) to
see it in action.

## Prerequisites

* Java 8
* Maven 3.5+

## Bisq configuration

Starting this api is like starting a normal Bisq client: configurations are
read and written to the same location on your disk.

This implies that:
* if you already have a fully configured Bisq application,
the api will use those pre-existing configurations.
* If this is the first time you use Bisq on your machine, you need to first
start with UI, so you can make all necessary configurations.

**Note**: running 2 Bisq instances on one computer (e.g one with API, one without)
is not advisable. By default they both write to the same configuration location.
Changing the appname can fix this issue.

## Compiling the API

This step needs to be done before you can start the API with UI or headless:

```mvn clean install```

## Getting started: with UI

The following command will start a GUI Bisq instance on
Bitcoin mainnet (meaning you can lose real BTC):

```mvn exec:java -Dexec.mainClass="io.bisq.api.app.BisqApiWithUIMain"```

## Getting started: headless

The following command will start a headless Bisq instance on
Bitcoin mainnet (meaning you can lose real BTC):

```mvn exec:java -Dexec.mainClass="io.bisq.api.app.ApiMain"```

## Developing

When testing it's advisable to run Bisq in REGTEST mode.
See below on how to pass Bisq arguments to enable REGTEST mode.
All regular Bisq arguments can be used.

```
mvn exec:java
-Dexec.mainClass="io.bisq.api.app.BisqApiWithUIMain"
-Dexec.args="--baseCurrencyNetwork=BTC_REGTEST --bitcoinRegtestHost localhost --nodePort 2003 --seedNodes=localhost:2225 --useLocalhost true --appName Bisq-Regtest-Bob"
```

## Exploring the HTTP API

locally generated swagger docs are at:
    http://localhost:8080/swagger

publically generated swagger docs are at:
    https://mrosseel.github.io/bisq-api-examples/

Locally generated **swagger.json** can be found at:
    http://localhost:8080/swagger.json

the admin interface can be found at:
    http://localhost:8080/admin[

## Overriding http port

Set the environment variable `BISQ_API_PORT` to your desired port.

## TODO

* produce jar with all dependencies for easier deployment
* Dockerfile to get up-and-running without having java/maven/git/...
* fix the Bisq dependency to the latest release, once it's released (post 0.5.3)


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
You have to build bisq-api and bisq-seednode images before running integration tests.
(bisq-seednode image sources reside in [exchange project](https://github.com/bisq-network/exchange/tree/master/seednode))

Run integration tests:

    mvn verify -P integration

## Build

In order to build shaded jar:

    mvn package -P shade


Api naming guidelines:

* resource names should be in plural
* use hyphen-case for multi word resource names (camelCase for everything else: payload properties or query params)
* if you need to return array, use {results,total} pattern in case pagination is required in future
* use GET / to search
* use POST / to create
* use PUT /{id} to update by id
* use DELETE /{id} to remove by id
* use POST /{id}/{action} to perform some action on specific resource
