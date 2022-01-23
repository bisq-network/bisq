#! /bin/bash

# Demonstrates a way to always keep one offer in the market, using the API CLI with a local regtest bitcoin node.
# Alice creates an offer, waits for Bob to take it, and completes the trade protocol with him.  Then Alice
# creates a new offer...
#
# Stop the script by entering ^C.
#
# A country code argument is used to create a country based face to face payment account for the simulated offer.
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
#     `$ apitest/scripts/rolling-offer-simulation.sh -d buy -c us -m 2.00 -a 0.125`
#
#  Script options:  -d <direction> -c <country-code> (-m <mkt-price-margin(%)> || -f <fixed-price>) -a <amount(btc)>
#
# Example:
#
#    Create a buy/usd offer to sell 0.1 btc at 2% above market price, using a US face to face payment account:
#
#       `$ apitest/scripts/rolling-offer-simulation.sh -d sell -c us -m 2.00 -a 0.1`


APP_BASE_NAME=$(basename "$0")
APP_HOME=$(pwd -P)
APITEST_SCRIPTS_HOME="$APP_HOME/apitest/scripts"

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"
source "$APITEST_SCRIPTS_HOME/trade-simulation-utils.sh"

checksetup
parseopts "$@"

printdate "Started $APP_BASE_NAME with parameters:"
printscriptparams
printbreak

registerdisputeagents

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

while : ; do
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
    genbtcblocks 3 2
    printbreak

    RANDOM_WAIT=$(echo $[$RANDOM % 10 + 1])
    printdate "Bob will take Alice's offer in $RANDOM_WAIT seconds..."
    sleeptraced "$RANDOM_WAIT"

    executetrade
    exitoncommandalert $?
    printbreak
done

exit 0
