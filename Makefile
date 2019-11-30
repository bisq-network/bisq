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
#  - JDK 10 to build and run Bisq binaries; see
#    https://www.oracle.com/java/technologies/java-archive-javase10-downloads.html
#
#
# USAGE
#
# The following commands (and a couple manual instructions) will get your
# localnet up and running quickly.
#
# STEP 1: Build all Bisq binaries and set up localnet resources. This will
# take a few minutes the first time through.
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
#     bisq-monitor
#     bisq-pricenode
#     bisq-relay
#     bisq-seednode
#     bisq-statsnode
#
#  - You will see a new '.localnet' directory containing the data dirs
#  for your regtest Bitcoin and Bisq nodes. Once you've deployed
#  them in the step below, the directory will look as follows:
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
#  a) Go to the Account screen in the Mediator instance and press CMD+N
#  and a popup will appear. Click 'Unlock' and then click 'Register' to
#  register the instance as a mediator.
#
#  b) While still in the Account screen, press CMD+D and follow the same
#  steps as above to register the instance as a refund agent.
#
# When the steps above are complete, your localnet should be up and
# ready to use. You can now test in isolation all Bisq features and use
# cases.
#

STATE_DIR := .localnet

# Set up everything necessary for deploying your localnet. This is the
# default target.
setup: build localnet

clean: clean-build clean-localnet

clean-build:
	./gradlew clean

clean-localnet:
	rm -rf $(STATE_DIR)
	rm -rf ./dao-setup

# Build all Bisq binaries and generate the shell scripts used to run
# them in the targets below
build:
	./gradlew build

# Unpack and customize a Bitcoin regtest node and Alice and Bob Bisq
# nodes that have been preconfigured with a blockchain containing the
# BSQ genesis transaction
localnet: clean-localnet
	# Unpack the old dao-setup.zip and move things around for more concise
	# and intuitive naming. This is a temporary measure until we clean these
	# resources up more thoroughly.
	unzip docs/dao-setup.zip
	mv dao-setup $(STATE_DIR)
	mv $(STATE_DIR)/Bitcoin-regtest $(STATE_DIR)/bitcoind
	mv $(STATE_DIR)/bisq-BTC_REGTEST_Alice_dao $(STATE_DIR)/alice
	mv $(STATE_DIR)/bisq-BTC_REGTEST_Bob_dao $(STATE_DIR)/bob
	# Remove the preconfigured bitcoin.conf in favor of explicitly
	# parameterizing the invocation of bitcoind in the target below
	rm -v $(STATE_DIR)/bitcoind/bitcoin.conf
	# Avoid spurious 'runCommand' errors in the bitcoind log when nc
	# fails to bind to one of the listed block notification ports
	echo exit 0 >> $(STATE_DIR)/bitcoind/blocknotify

# Deploy a complete localnet by running all required Bitcoin and Bisq
# nodes, each in their own named screen window. If you are not a screen
# user, you'll need to run each of the make commands manually in a
# separate terminal or as a background job.
#
# NOTE: You MUST already be attached to a screen session for the
# following commands to work properly.
deploy: setup
	screen -t bitcoin make bitcoind
	sleep 2    # wait for bitcoind rpc server to start
	make block # generate a block to ensure Bisq nodes get dao-synced
	screen -t seednode make seednode
	screen -t seednode2 make seednode2
	screen -t alice make alice
	screen -t bob make bob
	screen -t mediator make mediator

bitcoind: localnet
	bitcoind \
		-regtest \
		-prune=0 \
		-txindex=1 \
		-server \
		-rpcuser=bisqdao \
		-rpcpassword=bsq \
		-datadir=$(STATE_DIR)/bitcoind \
		-blocknotify='$(STATE_DIR)/bitcoind/blocknotify %s'

seednode: build
	./bisq-seednode \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--fullDaoNode=true \
		--rpcUser=bisqdao \
		--rpcPassword=bsq \
		--rpcBlockNotificationPort=5120 \
		--nodePort=2002 \
		--userDataDir=$(STATE_DIR) \
		--appName=seednode

seednode2: build
	./bisq-seednode \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--fullDaoNode=true \
		--rpcUser=bisqdao \
		--rpcPassword=bsq \
		--rpcBlockNotificationPort=5121 \
		--nodePort=3002 \
		--userDataDir=$(STATE_DIR) \
		--appName=seednode2

mediator: build
	./bisq-desktop \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--nodePort=4444 \
		--appDataDir=$(STATE_DIR)/mediator \
		--appName=Mediator

alice: setup
	./bisq-desktop \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--nodePort=5555 \
		--fullDaoNode=true \
		--rpcUser=bisqdao \
		--rpcPassword=bsq \
		--rpcBlockNotificationPort=5122 \
		--genesisBlockHeight=111 \
		--genesisTxId=30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf \
		--appDataDir=$(STATE_DIR)/alice \
		--appName=Alice

bob: setup
	./bisq-desktop \
		--baseCurrencyNetwork=BTC_REGTEST \
		--useLocalhostForP2P=true \
		--useDevPrivilegeKeys=true \
		--nodePort=6666 \
		--appDataDir=$(STATE_DIR)/bob \
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

.PHONY: seednode
