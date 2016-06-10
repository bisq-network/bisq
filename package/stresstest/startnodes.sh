#!/bin/bash

cd ../../seednode/target

logpath=/Users/mk/Documents/_intellij/bitsquare/logs

mkdir $logpath

nohup java -jar SeedNode.jar hlitt7z4bec4kdh4.onion:8000 0 500 >/dev/null 2>$logpath/ST_0_seednode.log & 
sleep 40

cd ../../gui/target
nohup java -jar shaded.jar --app.name=BS_arb --maxConnections=120 >/dev/null 2>$logpath/ST_0_arb.log & 
sleep 40

delay=10
for i in `seq 1 10`;
  do
	echo $i
	nohup java -jar shaded.jar --app.name=BS_$i --maxConnections=120 >/dev/null 2>$logpath/ST_$i.log &  
	sleep $delay
  done  

# kill `ps -ef | grep java | grep -v grep | awk '{print $2}'`

