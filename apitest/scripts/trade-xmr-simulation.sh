#! /bin/bash

# Runs xmr <-> btc trading scenarios using the API CLI with a local regtest bitcoin node.
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
#     `$ apitest/scripts/trade-xmr-simulation.sh -d buy -f 0.05 -a 0.125`
#
#  Script options:  -d <direction> -m <mkt-price-margin(%)> -f <fixed-price> -a <amount(btc)>
#
# Examples:
#
#    Create a buy/xmr offer to buy 0.125 btc at an xmr fixed-price of 0.05 btc, using an xmr payment account:
#
#       `$ apitest/scripts/trade-xmr-simulation.sh -d buy -f 0.05 -a 0.125`
#
#    Create a sell/xmr offer to sell 0.125 btc at at an xmr mkt-price-margin of 0%, using using an xmr payment account:
#
#       `$ apitest/scripts/trade-xmr-simulation.sh -d sell -m 0.00 -a 0.125`

export APP_BASE_NAME=$(basename "$0")
export APP_HOME=$(pwd -P)
export APITEST_SCRIPTS_HOME="$APP_HOME/apitest/scripts"
export CURRENCY_CODE="XMR"
export ALICE_XMR_ADDRESS="44i8xZbd8ecaD6nQQrHjr1BwTp6QfGL22iWqHZKmU4QYSyr1F64XAxM4HgvQHxbny7ehfxemaA9LPDLz2wY3fxhB1bbMEco"
export BOB_XMR_ADDRESS="48xdBkXaCosPxcWwXRZdSGc33M9tYu6k9ga56dqkNrgsjQuJX16xW2qTyWTZstJpXXj87dj5p4H3y1xAfoVjAysoAYrXh2N"

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"
source "$APITEST_SCRIPTS_HOME/trade-simulation-utils.sh"

checksetup
parsexmrscriptopts "$@"

printdate "Started $APP_BASE_NAME with parameters:"
printscriptparams
printbreak

registerdisputeagents

# Demonstrate how to create an XMR altcoin payment account.

printdate "Create Alice's XMR Trading Payment Account."
# Note: Having problems passing a double quoted --account-name param to function.
CMD="$CLI_BASE --port=$ALICE_PORT createcryptopaymentacct --account-name=Alice_XMR_Account"
CMD+=" --currency-code=XMR --address=$ALICE_XMR_ADDRESS --trade-instant=false"
printdate "ALICE CLI: $CMD"
CMD_OUTPUT=$(createpaymentacct "$CMD")
echo "$CMD_OUTPUT"
printbreak
export ALICE_ACCT_ID=$(getnewpaymentacctid "$CMD_OUTPUT")
printdate "Alice's XMR payment-account-id: $ALICE_ACCT_ID"
exitoncommandalert $?
printbreak

printdate "Create Bob's XMR Trading Payment Account."
# Note: Having problems passing a double quoted --account-name param to function.
CMD="$CLI_BASE --port=$BOB_PORT createcryptopaymentacct --account-name=Bob_XMR_Account"
CMD+=" --currency-code=XMR --address=$BOB_XMR_ADDRESS --trade-instant=false"
printdate "BOB CLI: $CMD"
CMD_OUTPUT=$(createpaymentacct "$CMD")
echo "$CMD_OUTPUT"
printbreak
export BOB_ACCT_ID=$(getnewpaymentacctid "$CMD_OUTPUT")
printdate "Bob's XMR payment-account-id: $BOB_ACCT_ID"
exitoncommandalert $?
printbreak

# Alice creates an offer.
printdate "ALICE $ALICE_ROLE:  Creating $DIRECTION $CURRENCY_CODE offer with payment acct $ALICE_ACCT_ID."
CMD=$(gencreateoffercommand "$ALICE_PORT" "$ALICE_ACCT_ID")
printdate "ALICE CLI: $CMD"
OFFER_ID=$(createoffer "$CMD")
exitoncommandalert $?
printdate "ALICE $ALICE_ROLE:  Created offer with id: $OFFER_ID."
printbreak
sleeptraced 3

# Show Alice's new offer.
printdate "ALICE $ALICE_ROLE:  Looking at her new $DIRECTION $CURRENCY_CODE offer."
CMD="$CLI_BASE --port=$ALICE_PORT getoffer --offer-id=$OFFER_ID"
printdate "ALICE CLI: $CMD"
OFFER=$($CMD)
exitoncommandalert $?
echo "$OFFER"
printbreak
sleeptraced 3

# Generate some btc blocks.
printdate "Generating btc blocks after publishing Alice's offer."
genbtcblocks 3 1
printbreak

# Go through the trade protocol.
executetrade
exitoncommandalert $?
printbreak

# Get balances after trade completion.
printdate "Bob & Alice's balances after trade:"
printdate  "ALICE CLI:"
printbalances "$ALICE_PORT"
printbreak
printdate "BOB CLI:"
printbalances "$BOB_PORT"
printbreak

exit 0
