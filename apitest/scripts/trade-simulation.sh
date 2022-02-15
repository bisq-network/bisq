#! /bin/bash

# Demonstrates a fiat <-> btc trade using the API CLI with a local regtest bitcoin node.
#
# A country code argument is used to create a country based face to face payment account for the simulated
# trade, and the maker's face to face payment account's currency code is used when creating the offer.
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
#  - Only regtest btc can be bought or sold with the test payment account.
#
# Usage:
#
#  This script must be run from the root of the project, e.g.:
#
#     `$ apitest/scripts/trade-simulation.sh -d buy -c fr -m 3.00 -a 0.125`
#
#  Script options:  -d <direction> -c <country-code> -m <mkt-price-margin(%)> -f <fixed-price> -a <amount(btc)>
#
# Examples:
#
#    Create and take a buy/eur offer to buy 0.125 btc at a mkt-price-margin of 0%, using an Italy face to face
#    payment account:
#
#       `$ apitest/scripts/trade-simulation.sh -d buy -c it -m 0.00 -a 0.125`
#
#    Create and take a sell/eur offer to sell 0.125 btc at a fixed-price of 38,000 euros, using a France face to face
#    payment account:
#
#       `$ apitest/scripts/trade-simulation.sh -d sell -c fr -f 38000 -a 0.125`

export APP_BASE_NAME=$(basename "$0")
export APP_HOME=$(pwd -P)
export APITEST_SCRIPTS_HOME="$APP_HOME/apitest/scripts"

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"
source "$APITEST_SCRIPTS_HOME/trade-simulation-utils.sh"

checksetup
parseopts "$@"

printdate "Started $APP_BASE_NAME with parameters:"
printscriptparams
printbreak

# Demonstrate how to create a country based, face to face account.
showcreatepaymentacctsteps "Alice" "$ALICE_PORT"

CMD="$CLI_BASE --port=$ALICE_PORT createpaymentacct --payment-account-form=$APITEST_SCRIPTS_HOME/$F2F_ACCT_FORM"
printdate "ALICE CLI: $CMD"
CMD_OUTPUT=$(createpaymentacct "$CMD")
echo "$CMD_OUTPUT"
printbreak
export ALICE_ACCT_ID=$(getnewpaymentacctid "$CMD_OUTPUT")
export CURRENCY_CODE=$(getnewpaymentacctcurrency "$CMD_OUTPUT")
printdate "Alice's F2F payment-account-id: $ALICE_ACCT_ID, currency-code: $CURRENCY_CODE"
exitoncommandalert $?
printbreak

printdate "Bob creates his F2F payment account."
CMD="$CLI_BASE --port=$BOB_PORT createpaymentacct --payment-account-form=$APITEST_SCRIPTS_HOME/$F2F_ACCT_FORM"
printdate "BOB CLI: $CMD"
CMD_OUTPUT=$(createpaymentacct "$CMD")
echo "$CMD_OUTPUT"
printbreak
export BOB_ACCT_ID=$(getnewpaymentacctid "$CMD_OUTPUT")
export CURRENCY_CODE=$(getnewpaymentacctcurrency "$CMD_OUTPUT")
printdate "Bob's F2F payment-account-id: $BOB_ACCT_ID, currency-code: $CURRENCY_CODE"
exitoncommandalert $?
printbreak

# Alice creates an offer.
printdate "ALICE $ALICE_ROLE:  Creating $DIRECTION $CURRENCY_CODE offer with payment acct $ALICE_ACCT_ID."
CURRENT_PRICE=$(getcurrentprice "$ALICE_PORT" "$CURRENCY_CODE")
exitoncommandalert $?
printdate "Current Market Price: $CURRENT_PRICE"
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
