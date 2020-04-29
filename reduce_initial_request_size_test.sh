#!/bin/sh

install()
{
	mkdir -p installdir/$2
	cp bisq-$1 installdir/$2/
	cp -r lib installdir/$2/
}

declare -A cmd
cmd['bitcoind']="bitcoind -regtest -prune=0 -txindex=1 -peerbloomfilters=1 -server -rpcuser=bisqdao -rpcpassword=bsq -datadir=.localnet/bitcoind -blocknotify='.localnet/bitcoind/blocknotify %s'"
cmd['alice']="installdir/alice/bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=5555 --fullDaoNode=true --rpcUser=bisqdao --rpcPassword=bsq --rpcBlockNotificationPort=5122 --genesisBlockHeight=111 --genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf --appDataDir=.localnet/alice --appName=Alice"
cmd['bob']="installdir/bob/bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=6666 --appDataDir=.localnet/bob --appName=Bob"
cmd['mediator']="installdir/mediator/bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=4444 --appDataDir=.localnet/mediator --appName=Mediator"
cmd['seednode']="installdir/seednode/bisq-seednode --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --fullDaoNode=true --rpcUser=bisqdao --rpcPassword=bsq --rpcBlockNotificationPort=5120 --nodePort=2002 --userDataDir=.localnet --appName=seednode"
cmd['seednode2']="installdir/seednode2/bisq-seednode --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --fullDaoNode=true --rpcUser=bisqdao --rpcPassword=bsq --rpcBlockNotificationPort=5121 --nodePort=3002 --userDataDir=.localnet --appName=seednode2"

run()
{
	# create a new screen session named 'localnet'
	screen -dmS localnet
	# deploy each node in its own named screen window
	for target in \
			bitcoind \
			seednode \
			seednode2 \
			alice \
			bob \
			mediator; do \
#		echo "$target: ${cmd[$target]}"
		screen -S localnet -X screen -t $target; \
		screen -S localnet -p $target -X stuff "${cmd[$target]}\n"; \
	done;
	# give bitcoind rpc server time to start
	sleep 5
	# generate a block to ensure Bisq nodes get dao-synced
	make block
}

# - shut down anything again
staap()
{
	# kill all Bitcoind and Bisq nodes running in screen windows
	screen -S localnet -X at "#" stuff "^C"
	# quit all screen windows which results in killing the session
	screen -S localnet -X at "#" kill
	screen -wipe
}

# clean everything for a fresh test run
rm -rf .localnet
rm -rf installdir

# deploy configuration files and start bitcoind
make localnet

# start with 1.3.2 setup
# - get sources for 1.3.2
git checkout v1.3.2 -f

# - build initial binaries and file structure
./gradlew :seednode:build
./gradlew :desktop:build

# - install binaries
install seednode seednode
install seednode seednode2
install desktop alice
install desktop bob
install desktop mediator

# - fire up all of it
run

# - setup mediator/refund agent
read -n 1 -p "Configure mediator/refund agent! proceed?" mainmenuinput
read -n 1 -p "Create 2 offers and do one trade! proceed?" mainmenuinput

# - shut down everything
staap

# upgrade to PR
git checkout -f reduce_initial_request_size
./gradlew :seednode:build

# install seednode binaries
install seednode seednode

# fire up all of it
run

read -n 1 -p "Create 2 offers and do one trade! done. proceed:" mainmenuinput

# shut down anything again
staap

## install client binaries
install desktop alice

## fire up all of it
run

read -n 1 -p "Create 2 offers and do one trade! done. proceed:" mainmenuinput

# shut down anything again
staap
