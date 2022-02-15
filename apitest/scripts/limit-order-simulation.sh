#! /bin/bash

# Demonstrates a way to create a limit order (offer) using the API CLI with a local regtest bitcoin node.
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
#     `$ apitest/scripts/limit-order-simulation.sh -l 40000 -d buy -c fr -m 3.00 -a 0.125`
#
#  Script options:  -l <limit-price> -d <direction> -c <country-code> (-m <mkt-price-margin(%)> || -f <fixed-price>) -a <amount(btc)> [-w <price-poll-interval(s)>]
#
# Example:
#
#    Create a sell/eur offer to sell 0.125 btc at a fixed-price of 38,000 euros, using a France face to face
#    payment account, when the BTC market price rises to or above 40,000 EUR:
#
#       `$ apitest/scripts/limit-order-simulation.sh -l 40000 -d sell -c fr -m 0.00 -a 0.125`

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(pwd -P)
APITEST_SCRIPTS_HOME="$APP_HOME/apitest/scripts"

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"
source "$APITEST_SCRIPTS_HOME/trade-simulation-utils.sh"

checksetup
parselimitorderopts "$@"

printdate "Started $APP_BASE_NAME with parameters:"
printscriptparams
printbreak

editpaymentaccountform "$COUNTRY_CODE"
exitoncommandalert $?
cat "$APITEST_SCRIPTS_HOME/$F2F_ACCT_FORM"
printbreak

# Create F2F payment accounts for $COUNTRY_CODE, and get the $CURRENCY_CODE.
printdate "Creating Alice's face to face $COUNTRY_CODE payment account."
CMD="$CLI_BASE --port=$ALICE_PORT createpaymentacct --payment-account-form=$APITEST_SCRIPTS_HOME/$F2F_ACCT_FORM"
printdate "ALICE CLI: $CMD"
CMD_OUTPUT=$(createpaymentacct "$CMD")
exitoncommandalert $?
echo "$CMD_OUTPUT"
ALICE_ACCT_ID=$(getnewpaymentacctid "$CMD_OUTPUT")
exitoncommandalert $?
CURRENCY_CODE=$(getnewpaymentacctcurrency "$CMD_OUTPUT")
exitoncommandalert $?
printdate "ALICE F2F payment-account-id = $ALICE_ACCT_ID, currency-code = $CURRENCY_CODE."
printbreak

printdate "Creating Bob's face to face $COUNTRY_CODE payment account."
CMD="$CLI_BASE --port=$BOB_PORT createpaymentacct --payment-account-form=$APITEST_SCRIPTS_HOME/$F2F_ACCT_FORM"
printdate "BOB CLI: $CMD"
CMD_OUTPUT=$(createpaymentacct "$CMD")
exitoncommandalert $?
echo "$CMD_OUTPUT"
BOB_ACCT_ID=$(getnewpaymentacctid "$CMD_OUTPUT")
exitoncommandalert $?
CURRENCY_CODE=$(getnewpaymentacctcurrency "$CMD_OUTPUT")
exitoncommandalert $?
printdate "BOB F2F payment-account-id = $BOB_ACCT_ID, currency-code = $CURRENCY_CODE."
printbreak

# Bob & Alice now have matching payment accounts, now loop until the price limit is reached, then create an offer.
if [ "$DIRECTION" = "BUY" ]
then
    printdate "Create a BUY / $CURRENCY_CODE offer when the market price falls to or below $LIMIT_PRICE $CURRENCY_CODE."
else
    printdate "Create a SELL / $CURRENCY_CODE offer when the market price rises to or above $LIMIT_PRICE $CURRENCY_CODE."
fi

DONE=0
while : ; do
    if [ "$DONE" -ne 0 ]; then
        break
    fi

    CURRENT_PRICE=$(getcurrentprice "$ALICE_PORT" "$CURRENCY_CODE")
    exitoncommandalert $?
    printdate "Current Market Price: $CURRENT_PRICE"

    if [ "$DIRECTION" = "BUY" ] && [ "$CURRENT_PRICE" -le "$LIMIT_PRICE" ]; then
        printdate "Limit price reached."
        DONE=1
        break
    fi

    if [ "$DIRECTION" = "SELL" ] && [ "$CURRENT_PRICE" -ge "$LIMIT_PRICE" ]; then
        printdate "Limit price reached."
        DONE=1
        break
    fi

    sleep "$WAIT"
done

printdate "ALICE: Creating $DIRECTION $CURRENCY_CODE offer with payment acct $ALICE_ACCT_ID."
CMD="$CLI_BASE --port=$ALICE_PORT createoffer"
CMD+=" --payment-account=$ALICE_ACCT_ID"
CMD+=" --direction=$DIRECTION"
CMD+=" --currency-code=$CURRENCY_CODE"
CMD+=" --amount=$AMOUNT"
if [ -z "$MKT_PRICE_MARGIN" ]; then
    CMD+=" --fixed-price=$FIXED_PRICE"
else
    CMD+=" --market-price-margin=$MKT_PRICE_MARGIN"
fi
CMD+=" --security-deposit=50.0"
CMD+=" --fee-currency=BSQ"
printdate "ALICE CLI: $CMD"
OFFER_ID=$(createoffer "$CMD")
exitoncommandalert $?
printdate "ALICE: Created offer with id: $OFFER_ID."
printbreak
sleeptraced 3

# Show Alice's new offer.
printdate "ALICE:  Looking at her new $DIRECTION $CURRENCY_CODE offer."
CMD="$CLI_BASE --port=$ALICE_PORT getoffer --offer-id=$OFFER_ID"
printdate "ALICE CLI: $CMD"
OFFER=$($CMD)
exitoncommandalert $?
echo "$OFFER"
printbreak
sleeptraced 4

# Generate some btc blocks.
printdate "Generating btc blocks after publishing Alice's offer."
genbtcblocks 3 3
printbreak

# Show Alice's offer in Bob's CLI.
printdate "BOB:  Looking at $DIRECTION $CURRENCY_CODE offers."
CMD="$CLI_BASE --port=$BOB_PORT getoffers --direction=$DIRECTION --currency-code=$CURRENCY_CODE"
printdate "BOB CLI: $CMD"
OFFERS=$($CMD)
exitoncommandalert $?
echo "$OFFERS"

exit 0
