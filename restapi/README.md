# Rest API application

The Rest API application provides access to Bisq network data as well as Bisq DAO data.
It is used for Bisq 2 to request data about the DAO state as well as account age and account witness data for reputation use cases.


Program arguments to run 'RestApiMain' with Bitcoin Regtest and localhost mode:
```
--baseCurrencyNetwork=BTC_REGTEST
--useDevPrivilegeKeys=true
--useLocalhostForP2P=true
--appName=[your app name]
--fullDaoNode=true
--rpcUser=[Bitcoin rpc username]
--rpcPassword=[Bitcoin rpc password]
--rpcPort=18443
--rpcBlockNotificationPort=[port used in blocknotify]
```

To run 'RestApiMain' you need to have Bitcoin node running and have 'blocknotify' in the `bitcoin.conf` set up.


