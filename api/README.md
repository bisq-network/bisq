# Bisq HTTP API

**The API branch is under development! 
Do not use it in production environment at the current state!**

Enabling the API exposes some of Bisq's functionality for access over a http API.
You can run it either as the desktop application or as a headless application.

On that branch we start to implement feature by feature starting with the most simple one - `version`.


**Known issues**: Wallet password protection is not supported at the moment for the headless version. So if you have set 
 a wallet password when running the Desktop version and afterwards run the headless version it will get stuck at startup 
 expecting the wallet password. This feature will be implemented soon._

**Note**: If you have a Bisq application with BTC already set up it is recommended to use the optional `appName` argument to 
provide a different name and data directory so that the default Bisq application is not exposed via the API. That way 
your data and wallet from your default Bisq application are completely isolated from the API application. In the below 
commands we use the argument `--appName=bisq-API` to ensure you are not mixing up your default Bisq setup when 
experimenting with the API branch. You cannot run the desktop and the headless version in parallel as they would access 
the same data.

**Security**: Api uses HTTP transport which is not encrypted. Use the API only locally and do not expose it over 
public network interfaces.

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
    
#### Authentication

By default there is no password required for the API. We recommend that you set password as soon as possible: 

    curl -X POST "http://localhost:8080/api/v1/user/password" -H "Content-Type: application/json" -d "{\"newPassword\":\"string\"}"
    
Password digest and salt are stored in a `apipasswd` in Bisq data directory.
If you forget your password, just delete that file and restart Bisq.
    
Now you can access other endpoints by adding `Authorization` header to the request 
accordingly to [Basic Authentication](https://en.wikipedia.org/wiki/Basic_access_authentication) rules.
```
   Base64(username+":"+password)
```
Note that username is ignored and can be whatever, i.e. in following example username is empty string and password is `abc` 
(Base64 of `:abc` is `OmFiYw==`):

    curl -X GET "http://localhost:8080/api/v1/version" -H "authorization: Basic OmFiYw=="
    
## Run the API as headless application

    cd api
    ../gradlew run --args="--appName=bisq-API"
    
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
