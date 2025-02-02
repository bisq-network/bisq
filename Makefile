#
# INTRODUCTION
#
# This makefile is designed to help Bisq contributors get up and running
# as quickly as possible with a local regtest Bisq network deployment,
# or 'localnet' for short. A localnet is a complete and self-contained
# "mini Bisq network" suitable for development and end-to-end testing
# efforts.
#
#
# REQUIREMENTS
#
# You'll need the following to proceed:
#
#  - Linux, macOS or similar *nix with standard tools like `make`
#  - bitcoind and bitcoin-cli (`brew install bitcoin` on macOS)
#  - JDK 11 to build and run Bisq binaries; see
#    https://jdk.java.net/archive/
#
#
# USAGE
#
# The following commands (and a couple manual instructions) will get
# your localnet up and running quickly.
#
# STEP 1: Build all Bisq binaries and set up localnet resources. This
# will take a few minutes the first time through.
#
#     $ make
#
# Notes:
#
#  - When complete, you'll have a number of scripts available in the
#  root directory. They will be used in the make targets below to start
#  the various Bisq seed and desktop nodes that will make up your
#  localnet:
#
#     $ ls -1 bisq-*
#     bisq-desktop
#     bisq-seednode
#     bisq-statsnode
#
#  - You will see a new '.localnet' directory containing the data dirs
#  for your regtest Bitcoin and Bisq nodes. Once you've deployed them in
#  the step below, the directory will look as follows:
#
#     $ tree -d -L 1 .localnet
#     .localnet
#     ├── alice
#     ├── bitcoind
#     ├── bob
#     ├── mediator
#     ├── seednode
#     └── seednode2
#
# STEP 2: Deploy the Bitcoin and Bisq nodes that make up the localnet.
# Run each of the following in a SEPARATE TERMINAL WINDOW, as they are
# long-running processes.
#
#     $ make bitcoind
#     $ make seednode
#     $ make seednode2
#     $ make mediator
#     $ make alice
#     $ make bob
#
#  Tip: Those familiar with the `screen` terminal multiplexer can
#  automate the above by running the `deploy` target found below.
#
#  Notes:
#
#    - The 'seednode' targets launch headless Bisq nodes that help
#    desktop nodes discover other peers, as well as storing and
#    forwarding p2p network messages for nodes as they go on and
#    offline.
#
#    - As you run the 'mediator', 'alice' and 'bob' targets above,
#    you'll see a Bisq desktop node window appear for each. The Alice
#    and Bob instances represent two traders who can make and take
#    offers with one another. The Mediator instance represents a Bisq
#    contributor who can help resolve any technical problems or disputes
#    that come up between the two traders.
#
# STEP 3: Configure the mediator Bisq node. In order to make and take
# offers, Alice and Bob will need to have a mediator and a refund agent
# registered on the network. Follow the instructions below to complete
# that process:
#
#  a) Go to the Account screen in the Mediator instance and press CMD+D
#  and a popup will appear. Click 'Unlock' and then click 'Register' to
#  register the instance as a mediator.
#
#  b) While still in the Account screen, press CMD+N and follow the same
#  steps as above to register the instance as a refund agent.
#
# When the steps above are complete, your localnet should be up and
# ready to use. You can now test in isolation all Bisq features and use
# cases.
#

# Set up everything necessary for deploying your localnet. This is the
# default target.
.PHONY: build bitcoind seednode seednode2 mediator alice bob block blocks
setup: build .localnet

clean: clean-build clean-localnet

clean-build:
	./gradlew clean

clean-localnet:
	rm -rf .localnet ./dao-setup

# Build Bisq binaries and shell scripts used in the targets below
build: seednode/build desktop/build

seednode/build:
	./gradlew :seednode:build

desktop/build:
	./gradlew :desktop:build

# Unpack and customize a Bitcoin regtest node and Alice and Bob Bisq
# nodes that have been preconfigured with a blockchain containing the
# BSQ genesis transaction
.localnet:
	# Unpack the old dao-setup.zip and move things around for more
	# concise and intuitive naming. This is a temporary measure until we
	# clean these resources up more thoroughly.
	unzip docs/dao-setup.zip
	mv dao-setup .localnet
	mv .localnet/Bitcoin-regtest .localnet/bitcoind
	mv .localnet/bisq-BTC_REGTEST_Alice_dao .localnet/alice
	mv .localnet/bisq-BTC_REGTEST_Bob_dao .localnet/bob
	# Remove the preconfigured bitcoin.conf in favor of explicitly
	# parameterizing the invocation of bitcoind in the target below
	rm -v .localnet/bitcoind/bitcoin.conf
	# Avoid spurious 'runCommand' errors in the bitcoind log when nc
	# fails to bind to one of the listed block notification ports
	echo exit 0 >> .localnet/bitcoind/blocknotify

# Alias '.localnet' to 'localnet' so the target is discoverable in tab
# completion
localnet: .localnet

# Deploy a complete localnet by running all required Bitcoin and Bisq
# nodes, each in their own named screen window. If you are not a screen
# user, you'll need to manually run each of the targets listed below
# commands manually in a separate terminal or as background jobs.
deploy: setup
	# ensure localnet is not already deployed
	if screen -ls localnet | grep Detached; then false; fi
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
		screen -S localnet -X screen -t $$target; \
		screen -S localnet -p $$target -X stuff "make $$target\n"; \
	done;
	# give bitcoind rpc server time to start
	sleep 5
	# generate a block to ensure Bisq nodes get dao-synced
	make block

# Undeploy a running localnet by killing all Bitcoin and Bisq
# node processes, then killing the localnet screen session altogether
undeploy:
	# kill all Bitcoind and Bisq nodes running in screen windows
	screen -S localnet -X at "#" stuff "^C"
	# quit all screen windows which results in killing the session
	screen -S localnet -X at "#" kill
	# remove dead screens
	screen -wipe || true

bitcoind: .localnet
	bitcoind \
		-regtest \
		-prune=0 \
		-txindex=1 \
		-peerbloomfilters=1 \
		-server \
		-rpcuser=bisqdao \
		-rpcpassword=bsq \
		-datadir=.localnet/bitcoind \
		-blocknotify='.localnet/bitcoind/blocknotify %s'

seednode: seednode/build
	./bisq-seednode \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--fullDaoNode=true \
		--isBmFullNode=true \
		--rpcUser=bisqdao \
		--rpcPassword=bsq \
		--rpcBlockNotificationPort=5120 \
		--nodePort=2002 \
		--userDataDir=.localnet \
		--appName=seednode

seednode2: seednode/build
	./bisq-seednode \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--fullDaoNode=true \
		--isBmFullNode=true \
		--rpcUser=bisqdao \
		--rpcPassword=bsq \
		--rpcBlockNotificationPort=5121 \
		--nodePort=3002 \
		--userDataDir=.localnet \
		--appName=seednode2

mediator: desktop/build
	./bisq-desktop \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--nodePort=4444 \
		--appDataDir=.localnet/mediator \
		--appName=Mediator

alice: setup
	./bisq-desktop \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--nodePort=5555 \
		--fullDaoNode=true \
		--isBmFullNode=true \
		--rpcUser=bisqdao \
		--rpcPassword=bsq \
		--rpcBlockNotificationPort=5122 \
		--genesisBlockHeight=111 \
		--genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf \
		--appDataDir=.localnet/alice \
		--appName=Alice

bob: setup
	./bisq-desktop \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--nodePort=6666 \
		--appDataDir=.localnet/bob \
		--appName=Bob

# Generate a new block on your Bitcoin regtest network. Requires that
# bitcoind is already running. See the `bitcoind` target above.
block:
	bitcoin-cli \
		-regtest \
		-rpcuser=bisqdao \
		-rpcpassword=bsq \
		getnewaddress \
		| xargs bitcoin-cli \
				-regtest \
				-rpcuser=bisqdao \
				-rpcpassword=bsq \
				generatetoaddress 1

# Generate more than 1 block.
# Instead of running `make block` 24 times,
# you can now run `make blocks n=24`
blocks:
	bitcoin-cli \
    		-regtest \
    		-rpcuser=bisqdao \
    		-rpcpassword=bsq \
    		getnewaddress \
    		| xargs bitcoin-cli \
    				-regtest \
    				-rpcuser=bisqdao \
					-rpcpassword=bsq \
    				generatetoaddress $(n)
