#!/usr/bin/env bash

set -e

BOB_PORT=8081

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
cd ${SCRIPT_DIR}
source ./offer_make_preconditions.sh

BOB_PAYMENT_ACCOUNT_ID=`curl -X POST -s  "http://localhost:$BOB_PORT/api/v1/payment-accounts" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"paymentMethod\": \"SEPA\", \"selectedTradeCurrency\": \"EUR\", \"tradeCurrencies\": [ \"EUR\" ],\"accountName\":\"SEPA EUR\",\"countryCode\":\"PL\",\"holderName\":\"Bob\",\"bic\":\"YJGSBIF70Q7\",\"iban\":\"EE875639607137003809\",\"acceptedCountries\":[\"PL\"]}" | jq -r '.id'`
curl -X POST -s "http://localhost:$BOB_PORT/api/v1/arbitrators/$ARBITRATOR_ADDRESS/select"

BOB_BISQ_WALLET=`curl -X POST -s "http://localhost:$BOB_PORT/api/v1/wallet/btc/addresses" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"context\": \"AVAILABLE\", \"unused\": true}" | jq -r '.address'`

docker exec -it ${BITCOIN_DOCKER_CONTAINER} bitcoin-cli -regtest sendtoaddress ${BOB_BISQ_WALLET} 1
sleep 1
