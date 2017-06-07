#!/bin/bash

logpath=/home/bs/bisq/logs
datapath=/home/bs/.local/share

mkdir -p $logpath

cd ../../gui/target

delay=40
# edit start end index
for i in `seq 0 0`;
  do
	echo $i
	nohup java -jar shaded.jar --app.name=BS_$i --maxConnections=12 >/dev/null 2>$logpath/ST_$i.log &  
	sleep $delay
  done  

# kill `ps -ef | grep java | grep -v grep | awk '{print $2}'`

