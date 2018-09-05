#!/bin/bash

cd ../../seednode/target

logpath=/Users/dev/Documents/_intellij/bisq/logs
datapath="/Users/dev/Library/Application Support"
mkdir -p $logpath

# rm "$datapath/bisq_seed_node_hlitt7z4bec4kdh4.onion_8000/db/SequenceNumberMap"
# rm "$datapath/bisq_seed_node_hlitt7z4bec4kdh4.onion_8000/db/PersistedPeers"

# rm "$datapath/BS_arb/mainnet/db/SequenceNumberMap"
# rm "$datapath/BS_arb/mainnet/db/PersistedPeers"

nohup bisq-seednode hlitt7z4bec4kdh4.onion:8000 0 500 >/dev/null 2>$logpath/ST_0_seednode.log &
sleep 40

cd ../../build
nohup build/app/bin/bisq-desktop --app.name=BS_arb --maxConnections=12 >/dev/null 2>$logpath/ST_0_arb.log &

# kill `ps -ef | grep java | grep -v grep | awk '{print $2}'`

