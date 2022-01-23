#! /bin/bash

# Demonstrates a bsq <-> btc swap trade using the API CLI with a local regtest bitcoin node.
#
# Prerequisites:
#
#  - Linux or OSX with bash, Java 11-15 (JDK language compatibility 11), and bitcoin-core (v0.19 -  v22).
#
#  - Bisq must be fully built with apitest dao setup files installed.
#    Build command:  `./gradlew clean build :apitest:installDaoSetup`
#
#  - All supporting nodes must be run locally, in dev/dao/regtest mode:
#           bitcoind, seednode, arbdaemon, alicedaemon, bobdaemon
#
#    These should be run using the apitest harness.  From the root project dir, run:
#    `$ ./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon --shutdownAfterTests=false`
#
# Usage:
#
#  This script must be run from the root of the project, e.g.:
#
#     `$ apitest/scripts/bsqswap-simulation.sh -d buy -f 0.00005 -a 0.125`
#
#  Script options:  -d <direction> -c <country-code> -f <fixed-price> -a <amount(btc)>
#
# Examples:
#
#    Create and take a bsq swap offer to buy 0.05 btc at a fixed-price of 0.00005 bsq (per 1 btc):
#
#       `$ apitest/scripts/bsqswap-simulation.sh -d buy -a 0.05 -f 0.00005`
#
#    Create and take a bsq swap offer to buy 1 btc at a fixed-price of 0.00005 bsq (per 1 btc):
#
#       `$ apitest/scripts/bsqswap-simulation.sh -d buy -a 1 -f 0.0005`

export APP_BASE_NAME=$(basename "$0")
export APP_HOME=$(pwd -P)
export APITEST_SCRIPTS_HOME="$APP_HOME/apitest/scripts"

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"
source "$APITEST_SCRIPTS_HOME/trade-simulation-utils.sh"
source "$APITEST_SCRIPTS_HOME/bsqswap-simulation-utils.sh"

checksetup
parsebsqswaporderopts "$@"

printdate "Started $APP_BASE_NAME with parameters:"
printbsqswapscriptparams
printbreak

# Alice creates a bsq swap offer.
printdate "Alice creating BSQ swap offer: $DIRECTION $AMOUNT BTC for BSQ at fixed price of $FIXED_PRICE BTC per 1 BSQ."
CMD=$(gencreatebsqswapoffercommand "$ALICE_PORT" "$ALICE_ACCT_ID")
printdate "ALICE CLI: $CMD"
OFFER_ID=$(createbsqswapoffer "$CMD")
exitoncommandalert $?
printdate "Alice created bsq swap offer with id: $OFFER_ID."
printbreak
sleeptraced 2

# Show Alice's new bsq swap offer.
printdate "Alice looking at her new $DIRECTION $CURRENCY_CODE offer."
CMD="$CLI_BASE --port=$ALICE_PORT getoffer --offer-id=$OFFER_ID"
printdate "ALICE CLI: $CMD"
OFFER=$($CMD)
exitoncommandalert $?
echo "$OFFER"
printbreak
sleeptraced 2

# Generate 1 btc block.
printdate "Generating 1 btc block after publishing Alice's offer."
genbtcblocks 1 1
printbreak

# Execute the BSQ swap.
executebsqswap
exitoncommandalert $?
printbreak

# Get balances after trade completion.
printdate "Bob & Alice's balances after BSQ swap trade."
printdate  "ALICE CLI:"
printbalances "$ALICE_PORT"
printbreak
printdate "BOB CLI:"
printbalances "$BOB_PORT"
printbreak

exit 0
