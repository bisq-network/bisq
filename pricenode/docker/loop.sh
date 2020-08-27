#!/bin/bash
while true
do
echo `date`  "(Re)-starting node"
java -jar ./build/libs/bisq-pricenode.jar 2 2
echo `date` "node terminated unexpectedly!!"
sleep 3
done
