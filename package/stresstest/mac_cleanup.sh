#!/bin/bash

dir=/Users/dev/Desktop/10_offers

for i in `seq 1 20`;
  do
	echo $i
	rm $dir/BS_$i/mainnet/tor/tor.real
	rm $dir/BS_$i/mainnet/tor/geoip
	rm $dir/BS_$i/mainnet/tor/geoip6
	rm $dir/BS_$i/mainnet/tor/cached-microdescs
	rm $dir/BS_$i/mainnet/tor/cached-microdesc-consensus
	rm $dir/BS_$i/mainnet/db/SequenceNumberMap
	rm $dir/BS_$i/mainnet/db/PersistedPeers
	rm -R -f $dir/BS_$i/mainnet/tor/__MACOSX
	rm -R -f $dir/BS_$i/mainnet/tor/hiddenservice/backup
	rm -R -f $dir/BS_$i/mainnet/keys/backup
	rm -R -f $dir/BS_$i/mainnet/db/backup
	rm -R -f $dir/BS_$i/mainnet/bitcoin/backup
  done  