#!/bin/bash

cd ../../seednode/target

logpath=/Users/mk/Documents/_intellij/bitsquare

nohup java -jar SeedNode.jar hlitt7z4bec4kdh4.onion:8000 0 500 >/dev/null 2>$logpath/stresstest_seednode.log & 
sleep 40

cd ../../gui/target
nohup java -jar shaded.jar --app.name=BS_arb --maxConnections=500 >/dev/null 2>$logpath/stresstest_arb.log & 
sleep 40

delay=5
for i in `seq 1 10`;
	do
	echo $i
	nohup java -jar shaded.jar --app.name=BS_$i --maxConnections=500 >/dev/null 2>$logpath/stresstest_$i.log &  
	sleep $delay
done  

# kill `ps -ef | grep java | grep -v grep | awk '{print $2}'`

