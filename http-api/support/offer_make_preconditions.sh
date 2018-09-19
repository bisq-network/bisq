#!/usr/bin/env bash

set -e

ALICE_PORT=8080
ARBITRATOR_PORT=8082
BITCOIN_DOCKER_CONTAINER=bisqapi_bitcoin_1

ALICE_PAYMENT_ACCOUNT_ID=`curl -X POST -s "http://localhost:$ALICE_PORT/api/v1/payment-accounts" -H "accept: application/json" -H "Content-Type: application/json" -d "{\"paymentMethod\":\"SEPA\",\"selectedTradeCurrency\":\"EUR\",\"tradeCurrencies\":[\"EUR\"],\"accountName\":\"SEPA EUR\",\"countryCode\":\"PL\",\"holderName\":\"Alice\",\"bic\":\"YJGSBIF70Q7\",\"iban\":\"EE875639607137003809\",\"acceptedCountries\":[\"PL\"]}" | jq -r '.id'`
curl -X POST -s "http://localhost:$ARBITRATOR_PORT/api/v1/arbitrators" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"languageCodes\": [ \"en\" ]}"
sleep 1
ARBITRATOR_ADDRESS=`curl -X GET -s "http://localhost:$ALICE_PORT/api/v1/arbitrators" -H "accept: application/json" | jq -r '.arbitrators[0].address'`
curl -X POST -s "http://localhost:$ALICE_PORT/api/v1/arbitrators/$ARBITRATOR_ADDRESS/select"
ALICE_BISQ_WALLET=`curl -X POST -s "http://localhost:$ALICE_PORT/api/v1/wallet/addresses" -H "accept: application/json" | jq -r '.address'`

docker exec -it ${BITCOIN_DOCKER_CONTAINER} bitcoin-cli -regtest generate 101
docker exec -it ${BITCOIN_DOCKER_CONTAINER} bitcoin-cli -regtest sendtoaddress ${ALICE_BISQ_WALLET} 1
sleep 1
