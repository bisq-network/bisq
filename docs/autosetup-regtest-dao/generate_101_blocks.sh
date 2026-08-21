#!/bin/bash

function is_connected(){
	sleep 1
	blocks=$(bitcoin-cli -datadir=. -regtest getblockcount)
	if [ $? -eq 0 ];then
		echo "yes"
	else
		echo "no"
	fi
}

connected=$(is_connected)
until [ "$connected" == "yes" ];do
	connected=$(is_connected)
done

blocks=$(bitcoin-cli -datadir=. -regtest getblockcount)
if [ $? -eq 0 ] && [ $blocks -lt 101 ];then
	echo "$0: found less blocks then 101, generating ..."
	bitcoin-cli -datadir=. -regtest generate 101
	echo "$0: done, exiting"
	exit 0
fi
echo "$0: nothing to do, exiting"
