[![Release]](https://jitpack.io/v/mrosseel/bisq-api.svg)
(https://jitpack.io/#mrosseel/bisq-api)

# Bisq API

**Based on proposal: [Bisq-IP 1](https://github.com/mrosseel/bisq-proposals/blob/api-proposal/http-api.adoc)**

This repository generates an artifact (jar file) which will be used by the  bisq-exchange project to provide 
API functionality. At the moment it cannot be used as a standalone project.

## Enabling the api

When starting Bisq, use the switch `--enableApi true`

For example:
```
java -jar shaded.jar --enableAPI true --baseCurrencyNetwork=BTC_REGTEST --bitcoinRegtestHost localhost 
     --btcNodes localhost:18444 --nodePort 2003 --seedNodes=localhost:2225 --useLocalhost true --appName Bisq-Regtest-Bob
```  

## Exploring the REST API

generated swagger docs are at:
http://localhost:8080/swagger

swagger.json can be found at (generate clients with swagger editor):
http://localhost:8080/swagger.json

the admin interface can be found at:
http://localhost:8080/admin[

## Overriding http port

Set the environment variable `BISQ_API_PORT` to your desired port.

## TODO

Candidate for hosting the docs:
https://github.com/Rebilly/ReDoc
