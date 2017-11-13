[![Release](https://jitpack.io/v/mrosseel/bisq-api.svg)]
(https://jitpack.io/#mrosseel/bisq-api)

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
