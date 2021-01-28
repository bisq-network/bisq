#! /bin/bash

# This file must be sourced by the main driver.

source "$APITEST_SCRIPTS_HOME/trade-simulation-env.sh"

printdate() {
    echo "[$(date)] $@"
}

printbreak() {
    echo ""
    echo ""
}

printcmd() {
    echo -en "$@\n"
}

sleeptraced() {
    PERIOD="$1"
    printdate "sleeping for $PERIOD"
    sleep "$PERIOD"
}

commandalert() {
    # Used in a script function when it needs to fail early with an error message, & pass the error code to the caller.
    # usage: commandalert <$?> <msg-prefix>
    if [ "$1" -ne 0 ]
    then
        printdate "Error: $2" >&2
        exit "$1"
    fi
}

# TODO rename exitonalert ?
exitoncommandalert() {
    # Used in a parent script when you need it to fail immediately, with no error message.
    # usage: exitoncommandalert <$?>
    if [ "$1" -ne 0 ]
    then
        exit "$1"
    fi
}

registerdisputeagents() {
    # Silently register dev dispute agents.  It's easy to forget.
    REG_KEY="6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a"
    CMD="$CLI_BASE --port=$ARBITRATOR_PORT registerdisputeagent --dispute-agent-type=mediator --registration-key=$REG_KEY"
    SILENT=$($CMD)
    commandalert $? "Could not register dev/test mediator."
    CMD="$CLI_BASE --port=$ARBITRATOR_PORT registerdisputeagent --dispute-agent-type=refundagent --registration-key=$REG_KEY"
    SILENT=$($CMD)
    commandalert $? "Could not register dev/test refundagent."
    # Do something with $SILENT to keep codacy happy.
    echo "$SILENT"  > /dev/null
}

getbtcoreaddress() {
    CMD="bitcoin-cli -regtest  -rpcport=19443 -rpcuser=apitest -rpcpassword=apitest getnewaddress"
    NEW_ADDRESS=$($CMD)
    echo "$NEW_ADDRESS"
}

genbtcblocks() {
	NUM_BLOCKS="$1"
	SECONDS_BETWEEN_BLOCKS="$2"
	ADDR_PARAM="$(getbtcoreaddress)"
	CMD_PREFIX="bitcoin-cli -regtest -rpcport=19443 -rpcuser=apitest -rpcpassword=apitest generatetoaddress 1"
	# Print the generatetoaddress command with double quoted address param, to make it cut & pastable from the console.
	printdate "$CMD_PREFIX \"$ADDR_PARAM\""
	# Now create the full generatetoaddress command to be run now.
    CMD="$CMD_PREFIX $ADDR_PARAM"
	for i in $(seq -f "%02g" 1 "$NUM_BLOCKS")
	do
        NEW_BLOCK_HASH=$(genbtcblock "$CMD")
        printdate "Block Hash #$i:$NEW_BLOCK_HASH"
        sleep "$SECONDS_BETWEEN_BLOCKS"
	done
}

genbtcblock() {
    CMD="$1"
    NEW_BLOCK_HASH=$($CMD | sed -n '2p')
    echo "$NEW_BLOCK_HASH"
}

escapepluschar() {
    STRING="$1"
    NEW_STRING=$(echo "${STRING//+/\\+}")
    echo "$NEW_STRING"
}

printbalances() {
    PORT="$1"
    printcmd "$CLI_BASE --port=$PORT getbalance"
    $CLI_BASE --port="$PORT" getbalance
}

getpaymentaccountmethods() {
    CMD="$1"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get payment method ids."
    printdate "Payment Method IDs:"
    echo "$CMD_OUTPUT"
}

getpaymentaccountform() {
    CMD="$1"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get new payment account form."
    echo "$CMD_OUTPUT"
}

editpaymentaccountform() {
    COUNTRY_CODE="$1"
    CMD="python3 $APITEST_SCRIPTS_HOME/editf2faccountform.py $COUNTRY_CODE"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not edit payment account form."
    printdate "Saved payment account form as $F2F_ACCT_FORM."
}

getnewpaymentacctid() {
    CREATE_PAYMENT_ACCT_OUTPUT="$1"
    PAYMENT_ACCT_DETAIL=$(echo -e "$CREATE_PAYMENT_ACCT_OUTPUT" | sed -n '3p')
    ACCT_ID=$(echo -e "$PAYMENT_ACCT_DETAIL" | awk '{print $NF}')
    echo "$ACCT_ID"
}

getnewpaymentacctcurrency() {
    CREATE_PAYMENT_ACCT_OUTPUT="$1"
    PAYMENT_ACCT_DETAIL=$(echo -e "$CREATE_PAYMENT_ACCT_OUTPUT" | sed -n '3p')
    # This is brittle; it requires the account name field to have N words,
    # e.g, "Face to Face Payment Account" as defined in editf2faccountform.py.
    CURRENCY_CODE=$(echo -e "$PAYMENT_ACCT_DETAIL" | awk '{print $6}')
    echo "$CURRENCY_CODE"
}

createpaymentacct() {
    CMD="$1"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not create new payment account."
    echo "$CMD_OUTPUT"
}

getpaymentaccounts() {
    PORT="$1"
    printcmd "$CLI_BASE --port=$PORT getpaymentaccts"
    CMD="$CLI_BASE --port=$PORT getpaymentaccts"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get payment accounts."
    echo "$CMD_OUTPUT"
}

showcreatepaymentacctsteps() {
    USER="$1"
    PORT="$2"
    printdate "$USER looks for the ID of the face to face payment account method (Bob will use same payment method)."
    CMD="$CLI_BASE --port=$PORT getpaymentmethods"
    printdate "$USER CLI: $CMD"
    PAYMENT_ACCT_METHODS=$(getpaymentaccountmethods "$CMD")
    echo "$PAYMENT_ACCT_METHODS"
    printbreak

    printdate "$USER uses the F2F payment method id to create a face to face payment account in country $COUNTRY_CODE."
    CMD="$CLI_BASE --port=$PORT getpaymentacctform --payment-method-id=F2F"
    printdate "$USER CLI: $CMD"
    getpaymentaccountform "$CMD"
    printbreak

    printdate "$USER edits the $COUNTRY_CODE payment account form, and (optionally) renames it as $F2F_ACCT_FORM"
    editpaymentaccountform "$COUNTRY_CODE"
    cat "$APITEST_SCRIPTS_HOME/$F2F_ACCT_FORM"

    # Remove the autogenerated json template because we are going to use one created by a python script in the next step.
    CMD="rm -v $APP_HOME/f2f_*.json"
    DELETE_JSON_TEMPLATE=$($CMD)
    printdate "$DELETE_JSON_TEMPLATE"
    printbreak
}

gencreateoffercommand() {
    PORT="$1"
    ACCT_ID="$2"
    CMD="$CLI_BASE --port=$PORT createoffer"
    CMD+=" --payment-account=$ACCT_ID"
    CMD+=" --direction=$DIRECTION"
    CMD+=" --currency-code=$CURRENCY_CODE"
    CMD+=" --amount=$AMOUNT"
    if [ -z "$MKT_PRICE_MARGIN" ]; then
        CMD+=" --fixed-price=$FIXED_PRICE"
    else
        CMD+=" --market-price-margin=$MKT_PRICE_MARGIN"
    fi
    CMD+=" --security-deposit=15.0"
    CMD+=" --fee-currency=BSQ"
    echo "$CMD"
}

createoffer() {
    CREATE_OFFER_CMD="$1"
    OFFER_DESC=$($CREATE_OFFER_CMD)

    # If the CLI command exited with an error, print the CLI error, and
    # return from this function now, passing the error status code to the caller.
    commandalert $? "Could not create offer."

    OFFER_DETAIL=$(echo -e "$OFFER_DESC" | sed -n '2p')
    NEW_OFFER_ID=$(echo -e "$OFFER_DETAIL" | awk '{print $NF}')
    echo "$NEW_OFFER_ID"
}

getfirstofferid() {
    PORT="$1"
    CMD="$CLI_BASE --port=$PORT getoffers --direction=$DIRECTION --currency-code=$CURRENCY_CODE"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get current $DIRECTION / $CURRENCY_CODE offers."
    FIRST_OFFER_DETAIL=$(echo -e "$CMD_OUTPUT" | sed -n '2p')
    FIRST_OFFER_ID=$(echo -e "$FIRST_OFFER_DETAIL" | awk '{print $NF}')
    commandalert $? "Could parse the offer-id from the first listed offer."
    echo "$FIRST_OFFER_ID"
}

# This is a large function that should be broken up if it ever makes sense to not treat a trade
# execution simulation as an atomic operation.  But we are not testing api methods here, just
# demonstrating how to use them to get through the trade protocol.  It should work for any trade
# between Bob & Alice, as long as Alice is maker, Bob is taker, and the offer to be taken is the
# first displayed in Bob's getoffers command output.
executetrade() {
    # Bob list available offers.
    printdate "BOB $BOB_ROLE:  Looking at $DIRECTION $CURRENCY_CODE offers."
    CMD="$CLI_BASE --port=$BOB_PORT getoffers --direction=$DIRECTION --currency-code=$CURRENCY_CODE"
    printdate "BOB CLI: $CMD"
    OFFERS=$($CMD)
    exitoncommandalert $?
    echo "$OFFERS"
    printbreak

    OFFER_ID=$(getfirstofferid "$BOB_PORT")
    exitoncommandalert $?
    printdate "First offer found: $OFFER_ID"

    # Take Alice's offer.
    CMD="$CLI_BASE --port=$BOB_PORT takeoffer --offer-id=$OFFER_ID --payment-account=$BOB_ACCT_ID --fee-currency=bsq"
    printdate "BOB CLI: $CMD"
    TRADE=$($CMD)
    commandalert $? "Could not take offer."
    # Print the takeoffer command's console output.
    printdate "$TRADE"
    printbreak
    sleeptraced 10

    # Generate some btc blocks
    printdate "Generating btc blocks after Bob takes Alice's offer."
    genbtcblocks 3 3
    printbreak
    sleeptraced 5

    # Send payment sent and received messages.
    if [ "$DIRECTION" = "BUY" ]
    then
        PAYER="ALICE $ALICE_ROLE"
        PAYER_PORT=$ALICE_PORT
        PAYER_CLI="ALICE CLI"
        PAYEE="BOB $BOB_ROLE"
        PAYEE_PORT=$BOB_PORT
        PAYEE_CLI="BOB CLI"
    else
        PAYER="BOB $BOB_ROLE"
        PAYER_PORT=$BOB_PORT
        PAYER_CLI="BOB CLI"
        PAYEE="ALICE $ALICE_ROLE"
        PAYEE_PORT=$ALICE_PORT
        PAYEE_CLI="ALICE CLI"
    fi

    # Confirm payment started.
    printdate "$PAYER:  Sending fiat payment sent msg."
    CMD="$CLI_BASE --port=$PAYER_PORT confirmpaymentstarted --trade-id=$OFFER_ID"
    printdate "$PAYER_CLI: $CMD"
    SENT_MSG=$($CMD)
    commandalert $? "Could not send confirmpaymentstarted message."
    # Print the confirmpaymentstarted command's console output.
    printdate "$SENT_MSG"
    printbreak

    sleeptraced 2
    printdate "Generating btc blocks after fiat payment sent msg."
    genbtcblocks 3 5
    sleeptraced 2
    printbreak

    # Confirm payment received.
    printdate "$PAYEE:  Sending fiat payment received msg."
    CMD="$CLI_BASE --port=$PAYEE_PORT confirmpaymentreceived --trade-id=$OFFER_ID"
    printdate "$PAYEE_CLI: $CMD"
    RCVD_MSG=$($CMD)
    commandalert $? "Could not send confirmpaymentreceived message."
    # Print the confirmpaymentreceived command's console output.
    printdate "$RCVD_MSG"
    printbreak
    sleeptraced 4

    # Generate some btc blocks
    printdate "Generating btc blocks after fiat transfer."
    genbtcblocks 3 5
    printbreak
    sleeptraced 3

    # Complete the trade on the seller side.
    if [ "$DIRECTION" = "BUY" ]
    then
        printdate "BOB $BOB_ROLE:  Closing trade by keeping funds in Bisq wallet."
        CMD="$CLI_BASE --port=$BOB_PORT keepfunds --trade-id=$OFFER_ID"
        printdate "BOB CLI: $CMD"
    else
        printdate "ALICE (taker):  Closing trade by keeping funds in Bisq wallet."
        CMD="$CLI_BASE --port=$ALICE_PORT keepfunds --trade-id=$OFFER_ID"
        printdate "ALICE CLI: $CMD"
    fi
    KEEP_FUNDS_MSG=$($CMD)
    commandalert $? "Could close trade with keepfunds command."
    # Print the keepfunds command's console output.
    printdate "$KEEP_FUNDS_MSG"
    sleeptraced 5
    printbreak

    printdate "Trade $OFFER_ID complete."
}

getcurrentprice() {
    PORT="$1"
    CURRENCY_CODE="$2"
    CMD="$CLI_BASE --port=$PORT getbtcprice --currency-code=$CURRENCY_CODE"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get current market $CURRENCY_CODE price."
    FLOOR=$(echo "$CMD_OUTPUT" | cut -d'.' -f 1)
    commandalert $? "Could not get the floor of the current market $CURRENCY_CODE price."
    INTEGER=$(echo "$FLOOR" | tr -cd '[[:digit:]]')
    commandalert $? "Could not convert the current market $CURRENCY_CODE price string to an integer."
    echo "$INTEGER"
}
