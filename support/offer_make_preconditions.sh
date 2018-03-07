#!/usr/bin/env bash

ALICE_PORT=8080
ARBITRATOR_PORT=8082
BITCOIN_DOCKER_CONTAINER=bisqapi_bitcoin_1

curl -X POST "http://localhost:$ARBITRATOR_PORT/api/v1/arbitrators" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"languageCodes\": [ \"en\" ]}"
sleep 1
ARBITRATOR_ADDRESS=`curl -X GET -s "http://localhost:$ALICE_PORT/api/v1/arbitrators" -H "accept: application/json" | jq -r '.arbitrators[0].address'`
curl -X POST "http://localhost:$ALICE_PORT/api/v1/arbitrators/$ARBITRATOR_ADDRESS/select"
ALICE_BISQ_WALLET=`curl -X POST "http://localhost:$ALICE_PORT/api/v1/wallet/btc/addresses" -H "accept: application/json" -H "Content-Type: application/json" -d "{ \"context\": \"AVAILABLE\", \"unused\": true}" | jq -r '.address'`

docker exec -it ${BITCOIN_DOCKER_CONTAINER} bitcoin-cli -regtest generate 101
docker exec -it ${BITCOIN_DOCKER_CONTAINER} bitcoin-cli -regtest sendtoaddress ${ALICE_BISQ_WALLET} 1
