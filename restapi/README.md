# Rest API node

Simple headless node with a Rest API to provide access to Bisq network data as well as Bisq DAO data.
It is used for Bisq 2 to request data about the DAO state as well as account age and account witness data for reputation use cases.


To run 'RestApiMain' you need to have Bitcoin node running and have 'blocknotify' in the `bitcoin.conf` set up.


### Run Rest API node

Run the Gradle task:

```sh
./gradlew restapi:run
```

Or create a run scrip by:

```sh
./gradlew restapi:startBisqApp
```

And then run:

```sh
./bisq-restapi
```

### Customize with program arguments

Example program arguments for running at localhost with  Regtest:
```sh
./bisq-restapi \
    --baseCurrencyNetwork=BTC_REGTEST \
    --useDevPrivilegeKeys=true \
    --useLocalhostForP2P=true \
    --nodePort=3333 \
    --appName=bisq-BTC_REGTEST_restapi \
    --fullDaoNode=true \
    --rpcUser=[RPC USER] \
    --rpcPassword=[RPC PW] \
    --rpcPort=18443 \
    --rpcBlockNotificationPort=5123
```



