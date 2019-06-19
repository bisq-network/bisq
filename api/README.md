# Bisq HTTP API

**The API branch is under development! 
Do not use it in production environment at the current state!**

Enabling the API exposes some of Bisq's functionality for access over a http API.
You can run it either as the desktop application or as a headless application.

On that branch we start to implement feature by feature starting with the most simple one - `version`.


_**Known issues**: Wallet password protection is not supported at the moment for the headless version. So if you have set 
 a wallet password when running the Desktop version and afterwards run the headless version it will get stuck at startup 
 expecting the wallet password. This feature will be implemented soon._

_**Note**: 
If you have a Bisq application with BTC already set up it is recommended to use the optional `appName` argument to 
provide a different name and data directory so that the default Bisq application is not exposed via the API. That way 
your data and wallet from your default Bisq application are completely isolated from the API application. In the below 
commands we use the argument `--appName=bisq-API` to ensure you are not mixing up your default Bisq setup when 
experimenting with the API branch. You cannot run the desktop and the headless version in parallel as they would access 
the same data.

## Run the API as Desktop application

    cd desktop
    ../gradlew run --args="--desktopWithHttpApi=true --appName=bisq-API"
    
If the application has started up you should see following line in the logs:

    HTTP API started on localhost/127.0.0.1:8080
    
If you prefer another port or host use the arguments `--httpApiHost` and `--httpApiPort`.

### API Documentation

Documentation is available at http://localhost:8080/docs/

Sample call:

    curl http://localhost:8080/api/v1/version

## Run the API as headless application

    cd api
    ../gradlew run --args="--appName=bisq-API"
    
## Docker integration

First you need to build the docker image for the API:

    cd api
    docker-compose build

Start container with the API:
    
    docker-compose up alice

It will automatically start `bisq-api` (alice), `bitcoind` and `seednode` in regtest mode.

    curl localhost:8080/api/v1/version

## Host and port configuration

    ../gradlew run --args="--httpApiHost=127.0.0.1 --httpApiPort=8080" 
    
**CAUTION! Please do not expose the API over a public interface**

## Experimental features

Some features will be not sufficiently tested and will only be enabled if you add the 
`enableHttpApiExperimentalFeatures` argument:

    ../gradlew run --args="--enableHttpApiExperimentalFeatures" 

## Regtest mode

    ../gradlew run --args="--appName=bisq-BTC_REGTEST-alice --nodePort=8003 --useLocalhostForP2P=true 
    --seedNodes=localhost:8000 --btcNodes=localhost:18445 --baseCurrencyNetwork=BTC_REGTEST --logLevel=info 
    --useDevPrivilegeKeys=true --bitcoinRegtestHost=NONE --myAddress=172.17.0.1:8003 
    --enableHttpApiExperimentalFeatures"

## Integration tests

Integration tests leverage Docker and run in headless mode. First you need to build docker images for the api:

    cd api
    docker-compose build
    ../gradlew testIntegration
    
IntelliJ Idea has awesome integration so just right click on `api/src/testIntegration` directory and select 
`Debug All Tests`.

### Integration tests logging

Due to Travis log length limitations the log level is set to WARN, but if you need to see more details locally
go to `ContainerFactory` class and set `ENV_LOG_LEVEL_VALUE` property to `debug`.
