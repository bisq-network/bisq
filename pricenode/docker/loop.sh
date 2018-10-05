#!/bin/bash
while true
do
echo `date`  "(Re)-starting node"
BITCOIN_AVG_PUBKEY=$BTCAVERAGE_PUBKEY BITCOIN_AVG_PRIVKEY=$BTCAVERAGE_PRIVKEY java -jar ./build/libs/bisq-pricenode.jar 2 2
echo `date` "node terminated unexpectedly!!"
sleep 3
done
