#!/usr/bin/env bash

set -e
set -x

BOB_PORT=8081

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
cd ${SCRIPT_DIR}
source ./offer_make_preconditions.sh
source ./offer_take_preconditions.sh

OFFER_ID=`curl -X POST -s "http://localhost:$ALICE_PORT/api/v1/offers" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"minAmount\": \"1\", \"accountId\": \"$ALICE_PAYMENT_ACCOUNT_ID\", \"amount\": \"1\", \"marketPair\": \"BTC_EUR\", \"percentageFromMarketPrice\": \"10.0\", \"priceType\": \"FIXED\", \"fundUsingBisqWallet\": \"true\", \"fixedPrice\": \"10\", \"direction\": \"BUY\"}" | jq -r '.offer_id'`
sleep 1

curl -X POST -s "http://localhost:$BOB_PORT/api/v1/offers/$OFFER_ID/take" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"paymentAccountId\": \"$BOB_PAYMENT_ACCOUNT_ID\", \"amount\": \"1\"}" | jq
