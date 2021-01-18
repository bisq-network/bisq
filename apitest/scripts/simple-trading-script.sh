#! /bin/bash

# Script for running simple fiat <-> btc trading scenarios using the API CLI on regtest.
#
# Prerequisites:
#
#  - Linux or OSX with bash, Java 10, or Java 11-12 (JDK language compatibility 10).
#
#  - Bisq must be fully built with apitest dao setup files installed.
#    Build command:  `./gradlew clean build :apitest:installDaoSetup`
#
#  - All supporting nodes must be run locally, in dev/dao/regtest mode:
#           bitcoind, seednode, arbdaemon, alicedaemon, bobdaemon
#
#    These should be run using the apitest harness.  From the root project dir, run:
#    `./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon --shutdownAfterTests=false`
#
#  - Only regtest btc can be bought or sold with the default dummy eur account.
#    The CLI supports creating other fiat payment accts but this script doesn't (todo).
#
# Usage:
#
#  This script must be run from the root of the project, e.g.:
#
#     `apitest/scripts/simple-trading-script.sh -d buy -c eur -m 3.00 -a 0.125`
#
#  Script options:  -d <direction> -c <currency-code> -m <mkt-price-margin(%)> - f <fixed-price> -a <amount(btc)>
#
# Examples:
#
#    Create a buy/eur offer to buy 0.125 btc at a mkt-price-margin of 0%:
#
#       `apitest/scripts/simple-trading-script.sh -d buy -c usd -m 0.00 -a 0.125`
#
#    Create a sell/eur offer to sell 0.125 btc at a fixed-price of 23,000 euros:
#
#       `apitest/scripts/simple-trading-script.sh -d sell -c usd -f 23000 -a 0.125`


APP_BASE_NAME=$(basename "$0")
APP_HOME=$(pwd -P)
APITEST_SCRIPTS_HOME="${APP_HOME}/apitest/scripts"

# Source the env and some helper functions.
. "${APITEST_SCRIPTS_HOME}/trading-script-env.sh"

checksetup
parseopts "$@"

printdate "Started ${APP_BASE_NAME} with parameters:"
printscriptparams
printbreak

registerdisputeagents

# Get balances.
printdate "Bob & Alice's balances before trade:"
printdate_sameline "ALICE CLI:"
printbalances "$ALICE_PORT"
printbreak

printdate_sameline "BOB CLI:"
printbalances "$BOB_PORT"
printbreak

printdate_sameline "ALICE CLI:"
getpaymentaccts "$ALICE_PORT"

ALICE_ACCT_ID=$(getdummyacctid "$ALICE_PORT")
printdate "ALICE ${ALICE_ROLE}: Fiat Acct ID: ${ALICE_ACCT_ID}"
printbreak

printdate_sameline "BOB CLI:"
getpaymentaccts "$BOB_PORT"

BOB_ACCT_ID=$(getdummyacctid "$BOB_PORT")
printdate "BOB ${BOB_ROLE}: Fiat Acct ID: ${BOB_ACCT_ID}"
printbreak

printdate "ALICE ${ALICE_ROLE}:  Creating ${DIRECTION} ${CURRENCY_CODE} offer with payment acct ${ALICE_ACCT_ID}."
CMD="$CLI_BASE --port=${ALICE_PORT} createoffer"
CMD+=" --payment-account=${ALICE_ACCT_ID}"
CMD+=" --direction=${DIRECTION}"
CMD+=" --currency-code=${CURRENCY_CODE}"
CMD+=" --amount=${AMOUNT}"
if [ -z "${MKT_PRICE_MARGIN}" ]; then
    CMD+=" --fixed-price=${FIXED_PRICE}"
else
    CMD+=" --market-price-margin=${MKT_PRICE_MARGIN}"
fi
CMD+=" --security-deposit=15.0"
CMD+=" --fee-currency=BSQ"
printdate_sameline "ALICE CLI:"
printcmd "$CMD"

OFFER_ID=$(createoffer "${CMD}")
exitoncommandalert $?
printdate "ALICE ${ALICE_ROLE}:  Created offer with id: ${OFFER_ID}."
printbreak
sleeptraced 10

# Generate some btc blocks.
printdate "Generating btc blocks after publishing Alice's offer."
genbtcblocks 3 5
printbreak
sleeptraced 10

# List offers.
printdate "BOB ${BOB_ROLE}:  Looking at ${DIRECTION} ${CURRENCY_CODE} offers."
CMD="$CLI_BASE --port=${BOB_PORT} getoffers --direction=${DIRECTION} --currency-code=${CURRENCY_CODE}"
printdate_sameline "BOB CLI:"
printcmd "$CMD"
OFFERS=$($CMD)
echo "${OFFERS}"
printbreak
sleeptraced 3

# Take offer.
printdate "BOB ${BOB_ROLE}:  Taking offer ${OFFER_ID} with payment acct ${BOB_ACCT_ID}."
CMD="$CLI_BASE --port=${BOB_PORT} takeoffer --offer-id=${OFFER_ID} --payment-account=${BOB_ACCT_ID} --fee-currency=bsq"
printdate_sameline "BOB CLI:"
printcmd "$CMD"
TRADE=$($CMD)
# Will exit if takeoffer cmd fails.
commandalert $? "Take offer command"

echo "${TRADE}"
printbreak
sleeptraced 10

# Generating some btc blocks
printdate "Generating btc blocks after Bob takes Alice's offer."
genbtcblocks 3 3
printbreak
sleeptraced 6

# Send payment sent and received messages.
if [ "${DIRECTION}" = "BUY" ]
then
    PAYER="ALICE ${ALICE_ROLE}"
    PAYER_PORT=${ALICE_PORT}
    PAYER_CLI="ALICE CLI"
    PAYEE="BOB ${BOB_ROLE}"
    PAYEE_PORT=${BOB_PORT}
    PAYEE_CLI="BOB CLI"
else
    PAYER="BOB ${BOB_ROLE}"
    PAYER_PORT=${BOB_PORT}
    PAYER_CLI="BOB CLI"
    PAYEE="ALICE ${ALICE_ROLE}"
    PAYEE_PORT=${ALICE_PORT}
    PAYEE_CLI="ALICE CLI"
fi

# Confirm payment started.
printdate "${PAYER}:  Sending fiat payment sent msg."
CMD="$CLI_BASE --port=${PAYER_PORT} confirmpaymentstarted --trade-id=${OFFER_ID}"
printdate_sameline "${PAYER_CLI}:"
printcmd "$CMD"
SENT_MSG=$($CMD)
# Will exit if confirmpaymentstarted cmd fails.
commandalert $? "The confirmpaymentstarted command"

printdate "${SENT_MSG}"
printbreak

sleeptraced 2
printdate "Generating btc blocks after fiat payment sent msg."
genbtcblocks 3 5
sleeptraced 2

# Confirm payment received.
printdate "${PAYEE}:  Sending fiat payment received msg."
CMD="$CLI_BASE --port=${PAYEE_PORT} confirmpaymentreceived --trade-id=${OFFER_ID}"
printdate_sameline "${PAYEE_CLI}:"
printcmd "$CMD"
RCVD_MSG=$($CMD)
commandalert $? "The confirmpaymentreceived command"
printdate "${RCVD_MSG}"
printbreak
sleeptraced 4


# Generate some btc blocks
printdate "Generating btc blocks after fiat transfer."
genbtcblocks 3 5
printbreak
sleeptraced 3

# Complete the trade on the seller side.
if [ "${DIRECTION}" = "BUY" ]
then
	printdate "BOB ${BOB_ROLE}:  Closing trade by keeping funds in Bisq wallet."
    CMD="$CLI_BASE --port=${BOB_PORT} keepfunds --trade-id=${OFFER_ID}"
    printdate_sameline "BOB CLI:"
    printcmd "$CMD"
else
	printdate "ALICE (taker):  Closing trade by keeping funds in Bisq wallet."
    CMD="$CLI_BASE --port=${ALICE_PORT} keepfunds --trade-id=${OFFER_ID}"
    printdate_sameline "ALICE CLI:"
    printcmd "$CMD"
fi
KEEP_FUNDS_MSG=$($CMD)
commandalert $? "The keepfunds command"
printdate "${KEEP_FUNDS_MSG}"
sleeptraced 5
printbreak


# Get balances after trade completion.
printdate "Bob & Alice's balances after trade:"
printdate_sameline "ALICE CLI:"
printbalances "$ALICE_PORT"
printbreak
printdate_sameline "BOB CLI:"
printbalances "$BOB_PORT"
printbreak

exit 0
