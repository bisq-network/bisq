#! /bin/bash

# This file must be sourced by the driver script.

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"
source "$APITEST_SCRIPTS_HOME/trade-simulation-utils.sh"

gencreatebsqswapoffercommand() {
    PORT="$1"
    CMD="$CLI_BASE --port=$PORT createoffer"
    CMD+=" --swap=true"
    CMD+=" --direction=$DIRECTION"
    CMD+=" --amount=$AMOUNT"
    CMD+=" --fixed-price=$FIXED_PRICE"
    CMD+=" --currency-code=$CURRENCY_CODE"
    echo "$CMD"
}

createbsqswapoffer() {
    CREATE_OFFER_CMD="$1"
    OFFER_DESC=$($CREATE_OFFER_CMD)

    # If the CLI command exited with an error, print the CLI error, and
    # return from this function now, passing the error status code to the caller.
    commandalert $? "Could not create offer."

    OFFER_DETAIL=$(echo -e "$OFFER_DESC" | sed -n '2p')
    NEW_OFFER_ID=$(echo -e "$OFFER_DETAIL" | awk '{print $NF}')
    echo "$NEW_OFFER_ID"
}

executebsqswap() {
    # Bob list available BSQ offers.  (If a v1 BSQ offer is picked this simulation will break.)
    printdate "Bob looking at $DIRECTION $CURRENCY_CODE offers."
    CMD="$CLI_BASE --port=$BOB_PORT getoffers --direction=$DIRECTION --currency-code=$CURRENCY_CODE"
    printdate "BOB CLI: $CMD"
    OFFERS=$($CMD)
    exitoncommandalert $?
    echo "$OFFERS"
    printbreak

    OFFER_ID=$(getfirstofferid "$BOB_PORT")
    exitoncommandalert $?
    printdate "First BSQ offer found: $OFFER_ID"

    # Take Alice's BSQ swap offer.
    CMD="$CLI_BASE --port=$BOB_PORT takeoffer --offer-id=$OFFER_ID"
    printdate "BOB CLI: $CMD"
    TRADE=$($CMD)
    commandalert $? "Could not take BSQ swap offer."
    # Print the takeoffer command's console output.
    printdate "$TRADE"
    printbreak

    # Generate 1 btc block
    printdate "Generating 1 btc block after BSQ swap execution."
    genbtcblocks 1 2
    printbreak

    printdate "BSQ swap trade $OFFER_ID complete."
    printbreak

    printdate "Alice looking at her trade with id $OFFER_ID."
    CMD="$CLI_BASE --port=$ALICE_PORT gettrade --trade-id=$OFFER_ID"
    printdate "ALICE CLI: $CMD"
    GETTRADE_CMD_OUTPUT=$(gettrade "$CMD")
    exitoncommandalert $?
    echo "$GETTRADE_CMD_OUTPUT"
    printbreak

    printdate "Bob looking at his trade with id $OFFER_ID."
    CMD="$CLI_BASE --port=$BOB_PORT gettrade --trade-id=$OFFER_ID"
    printdate "BOB CLI: $CMD"
    GETTRADE_CMD_OUTPUT=$(gettrade "$CMD")
    exitoncommandalert $?
    echo "$GETTRADE_CMD_OUTPUT"
    printbreak
}

