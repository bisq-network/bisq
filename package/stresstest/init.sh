#!/bin/bash

cd ../../seednode/target

logpath=/Users/mk/Documents/_intellij/bitsquare/logs
datapath="/Users/mk/Library/Application Support"
mkdir -p $logpath

nohup java -jar SeedNode.jar hlitt7z4bec4kdh4.onion:8000 0 500 >/dev/null 2>$logpath/ST_0_seednode.log & 
sleep 40

cd ../../gui/target
nohup java -jar shaded.jar --app.name=BS_arb --maxConnections=12 >/dev/null 2>$logpath/ST_0_arb.log & 

# kill `ps -ef | grep java | grep -v grep | awk '{print $2}'`

