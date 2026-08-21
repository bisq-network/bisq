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
#  - JDK 21 to build and run Bisq binaries
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
#  scripts directory. They will be used in the make targets below to start
#  the various Bisq seed and desktop nodes that will make up your
#  localnet:
#
#     $ ls -1 scripts
#     desktop
#     seednode
#     statsnode
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
#     $ ./gradlew :startFirstRegtestBitcoind
#     $ ./gradlew :startSecondRegtestBitcoind
#     $ ./gradlew :startRegtestFirstSeednode
#     $ ./gradlew :startRegtestSecondSeednode
#     $ ./gradlew :startRegtestMediator
#     $ ./gradlew :startRegtestAlice
#     $ ./gradlew :startRegtest
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
setup:
	./gradlew :seednode:build :desktop:build

clean: clean-build clean-localnet

clean-build:
	./gradlew clean

clean-localnet:
	rm -rf .localnet ./dao-setup

# Deploy a complete localnet by running all required Bitcoin and Bisq
# nodes, each in their own named screen window. If you are not a screen
# user, you'll need to manually run each of the targets listed below
# commands manually in a separate terminal or as background jobs.
deploy:
	./gradlew :startRegtest
	# give bitcoind rpc server time to start
	sleep 5
	# generate a block to ensure Bisq nodes get dao-synced
	make block

# Undeploy a running localnet by killing all Bitcoin and Bisq
# node processes, then killing the localnet screen session altogether
undeploy:
	./gradlew :stopRegtest

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
