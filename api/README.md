# Bisq HTTP API

**THIS IS EXPERIMENTAL SOFTWARE! 
USE AT YOUR OWN RISK!!!**

The goal of this module is to expose some of the Bisq's functionality for programmatic access.
HTTP API can be run together with desktop module or in headless mode.

_Note: In the following commands we use `foo` before the actual arguments because gradle does not allow that the first argument
 starts with a double dash. The `foo` has no meaning and is ignored._

## Desktop with HTTP API

    cd desktop
    ../gradlew run --args "foo --desktopWithHttpApi=true"
    
If all goes well you should see following line in the logs:

    HTTP API started on localhost/127.0.0.1:8080

### API Documentation

Documentation should be available at http://localhost:8080/docs/

Sample call:

    curl http://localhost:8080/api/v1/version

## Headless mode

    cd api
    ../gradlew run
    
## Docker integration

First you need to build docker image for the API:

    cd api
    docker-compose build

Start container with the API:
    
    docker-compose up alice

It will automatically start `bisq-api` (alice), `bitcoind` and `seednode` in regtest mode.

    curl localhost:8080/api/v1/version

## Host and port configuration

    ../gradlew run --args "foo --httpApiHost=0.0.0.0 --httpApiPort=8888" 
    
**CAUTION! Please do not expose the API over public interface (0.0.0.0 exposes on all interfaces)**

## Experimental features

Some features might be highly experimental and will work only when started with special flag:

    ../gradlew run --args "foo --enableHttpApiExperimentalFeatures" 

## Regtest mode

    ../gradlew run --args "foo --appName=bisq-BTC_REGTEST-alice --nodePort=8003 --useLocalhostForP2P=true --seedNodes=localhost:8000 --btcNodes=localhost:18445 --baseCurrencyNetwork=BTC_REGTEST --logLevel=info --useDevPrivilegeKeys=true --bitcoinRegtestHost=NONE --myAddress=172.17.0.1:8003 --enableHttpApiExperimentalFeatures"

## Integration tests

Integration tests leverage Docker and run in headless mode. First you need to build docker images for the api:

    cd api
    docker-compose build
    ../gradlew testIntegration
    
IntelliJ Idea has awesome integration so just right click on `api/src/testIntegration` directory and select `Debug All Tests`.
