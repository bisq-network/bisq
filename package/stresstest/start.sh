#!/bin/bash

logpath=/home/bs/bitsquare/logs
datapath=/home/bs/.local/share

mkdir -p $logpath

cd ../../gui/target

delay=5
for i in `seq 1 10`;
  do
	echo $i
	rm $datapath/BS_$i/mainnet/db/SequenceNumberMap
	rm $datapath/BS_$i/mainnet/db/PersistedPeers
	nohup java -jar shaded.jar --app.name=BS_$i --maxConnections=12 >/dev/null 2>$logpath/ST_$i.log &  
	sleep $delay
  done  

# kill `ps -ef | grep java | grep -v grep | awk '{print $2}'`

