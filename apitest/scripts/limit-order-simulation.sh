#! /bin/bash

APP_HOME=$(pwd -P)
APITEST_SCRIPTS_HOME="${APP_HOME}/apitest/scripts"

# Source the env and some helper functions.
. "${APITEST_SCRIPTS_HOME}/trade-simulation-env.sh"
. "${APITEST_SCRIPTS_HOME}/trade-simulation-utils.sh"

checksetup
parselimitorderopts "$@"

printdate "Started ${APP_BASE_NAME} with parameters:"
printscriptparams
printbreak

editpaymentaccountform "$COUNTRY_CODE"
exitoncommandalert $?
cat "${APITEST_SCRIPTS_HOME}/${F2F_ACCT_FORM}"
printbreak

# Create F2F payment accounts for $COUNTRY_CODE, and get the $CURRENCY_CODE.
printdate "Creating Alice's face to face ${COUNTRY_CODE} payment account."
CMD="${CLI_BASE} --port=${ALICE_PORT} createpaymentacct --payment-account-form=${APITEST_SCRIPTS_HOME}/${F2F_ACCT_FORM}"
printdate "ALICE CLI: ${CMD}"
CMD_OUTPUT=$(createpaymentacct "${CMD}")
exitoncommandalert $?
echo "${CMD_OUTPUT}"
ALICE_ACCT_ID=$(getnewpaymentacctid "${CMD_OUTPUT}")
exitoncommandalert $?
CURRENCY_CODE=$(getnewpaymentacctcurrency "${CMD_OUTPUT}")
exitoncommandalert $?
printdate "ALICE F2F payment-account-id = ${ALICE_ACCT_ID}, currency-code = ${CURRENCY_CODE}."
printbreak

printdate "Creating Bob's face to face ${COUNTRY_CODE} payment account."
CMD="${CLI_BASE} --port=${BOB_PORT} createpaymentacct --payment-account-form=${APITEST_SCRIPTS_HOME}/${F2F_ACCT_FORM}"
printdate "BOB CLI: ${CMD}"
CMD_OUTPUT=$(createpaymentacct "${CMD}")
exitoncommandalert $?
echo "${CMD_OUTPUT}"
BOB_ACCT_ID=$(getnewpaymentacctid "${CMD_OUTPUT}")
exitoncommandalert $?
CURRENCY_CODE=$(getnewpaymentacctcurrency "${CMD_OUTPUT}")
exitoncommandalert $?
printdate "BOB F2F payment-account-id = ${BOB_ACCT_ID}, currency-code = ${CURRENCY_CODE}."
printbreak

# Bob & Alice now have matching payment accounts, now loop until the price limit is reached, then create an offer.
if [ "$DIRECTION" = "BUY" ]
then
    printdate "Create a BUY / ${CURRENCY_CODE} offer when the market price falls to or below ${LIMIT_PRICE} ${CURRENCY_CODE}."
else
    printdate "Create a SELL / ${CURRENCY_CODE} offer when the market price rises to or above ${LIMIT_PRICE} ${CURRENCY_CODE}."
fi

DONE=0
while : ; do
    if [ "$DONE" -ne 0 ]; then
        break
    fi

    CURRENT_PRICE=$(getcurrentprice "${CURRENCY_CODE}")
    printdate "Current Price: ${CURRENT_PRICE} ${CURRENCY_CODE}"

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

    sleep 15
done

printdate "ALICE: Creating ${DIRECTION} ${CURRENCY_CODE} offer with payment acct ${ALICE_ACCT_ID}."
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
CMD+=" --security-deposit=50.0"
CMD+=" --fee-currency=BSQ"
printdate "ALICE CLI: ${CMD}"
OFFER_ID=$(createoffer "${CMD}")
exitoncommandalert $?
printdate "ALICE: Created offer with id: ${OFFER_ID}."
printbreak
sleeptraced 10

# Generate some btc blocks.
printdate "Generating btc blocks after publishing Alice's offer."
genbtcblocks 3 3
printbreak
sleeptraced 5

# Show Alice's offer in Bob's CLI.
printdate "BOB:  Looking at ${DIRECTION} ${CURRENCY_CODE} offers."
CMD="$CLI_BASE --port=${BOB_PORT} getoffers --direction=${DIRECTION} --currency-code=${CURRENCY_CODE}"
printdate "BOB CLI: ${CMD}"
OFFERS=$($CMD)
exitoncommandalert $?
echo "${OFFERS}"

exit 0
