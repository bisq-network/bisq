#!/bin/bash
while true
do
echo `date`  "(Re)-starting node"
java -jar ./provider/target/provider.jar $BTCAVERAGE_PRIVKEY $BTCAVERAGE_PUBKEY > /dev/null 2>errors.log
echo `date` "node terminated unexpectedly!!"
sleep 3
done
