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

gettrade() {
    GET_TRADE_CMD="$1"
    TRADE_DESC=$($GET_TRADE_CMD)
    commandalert $? "Could not get trade."
    echo "$TRADE_DESC"
}

gettradedetail() {
    TRADE_DESC="$1"
    # Get 2nd line of gettrade cmd output, and squeeze multi space delimiters into one space.
    TRADE_DETAIL=$(echo "$TRADE_DESC" | sed -n '2p' | tr -s ' ')
    commandalert $? "Could not get trade detail (line 2 of gettrade output)."
    echo "$TRADE_DETAIL"
}

istradedepositpublished() {
    TRADE_DETAIL="$1"
    ANSWER=$(echo "$TRADE_DETAIL" | awk '{print $10}')
    commandalert $? "Could not parse istradedepositpublished from trade detail."
    echo "$ANSWER"
}

istradedepositconfirmed() {
    TRADE_DETAIL="$1"
    ANSWER=$(echo "$TRADE_DETAIL" | awk '{print $11}')
    commandalert $? "Could not parse istradedepositconfirmed from trade detail."
    echo "$ANSWER"
}

istradepaymentsent() {
    TRADE_DETAIL="$1"
    ANSWER=$(echo "$TRADE_DETAIL" | awk '{print $13}')
    commandalert $? "Could not parse istradepaymentsent from trade detail."
    echo "$ANSWER"
}

istradepaymentreceived() {
    TRADE_DETAIL="$1"
    ANSWER=$(echo "$TRADE_DETAIL" | awk '{print $14}')
    commandalert $? "Could not parse istradepaymentreceived from trade detail."
    echo "$ANSWER"
}

istradepayoutpublished() {
    TRADE_DETAIL="$1"
    ANSWER=$(echo "$TRADE_DETAIL" | awk '{print $15}')
    commandalert $? "Could not parse istradepayoutpublished from trade detail."
    echo "$ANSWER"
}

waitfortradedepositpublished() {
    # Loops until Bob's trade deposit is published.  (Bob is always the trade taker.)
    OFFER_ID="$1"
    DONE=0
    while : ; do
        if [ "$DONE" -ne 0 ]; then
            break
        fi

        printdate "BOB $BOB_ROLE:  Looking at his trade with id $OFFER_ID."
        CMD="$CLI_BASE --port=$BOB_PORT gettrade --trade-id=$OFFER_ID"
        printdate "BOB CLI: $CMD"
        GETTRADE_CMD_OUTPUT=$(gettrade "$CMD")
        exitoncommandalert $?
        echo "$GETTRADE_CMD_OUTPUT"
        printbreak

        TRADE_DETAIL=$(gettradedetail "$GETTRADE_CMD_OUTPUT")
        exitoncommandalert $?

        IS_TRADE_DEPOSIT_PUBLISHED=$(istradedepositpublished "$TRADE_DETAIL")
        exitoncommandalert $?

        printdate "BOB $BOB_ROLE:  Has taker's trade deposit been published?  $IS_TRADE_DEPOSIT_PUBLISHED"
        if [ "$IS_TRADE_DEPOSIT_PUBLISHED" = "YES" ]
        then
            DONE=1
        else
            RANDOM_WAIT=$(echo $[$RANDOM % 3 + 1])
            sleeptraced "$RANDOM_WAIT"
        fi
        printbreak
    done
}

waitfortradedepositconfirmed() {
    # Loops until Bob's trade deposit is confirmed.  (Bob is always the trade taker.)
    OFFER_ID="$1"
    DONE=0
    while : ; do
        if [ "$DONE" -ne 0 ]; then
            break
        fi

        printdate "BOB $BOB_ROLE:  Looking at his trade with id $OFFER_ID."
        CMD="$CLI_BASE --port=$BOB_PORT gettrade --trade-id=$OFFER_ID"
        printdate "BOB CLI: $CMD"
        GETTRADE_CMD_OUTPUT=$(gettrade "$CMD")
        exitoncommandalert $?
        echo "$GETTRADE_CMD_OUTPUT"
        printbreak

        TRADE_DETAIL=$(gettradedetail "$GETTRADE_CMD_OUTPUT")
        exitoncommandalert $?

        IS_TRADE_DEPOSIT_CONFIRMED=$(istradedepositconfirmed "$TRADE_DETAIL")
        exitoncommandalert $?
        printdate "BOB $BOB_ROLE:  Has taker's trade deposit been confirmed?  $IS_TRADE_DEPOSIT_CONFIRMED"
        printbreak

        if [ "$IS_TRADE_DEPOSIT_CONFIRMED" = "YES" ]
        then
            DONE=1
        else
            printdate "Generating btc block while Bob waits for trade deposit to be confirmed."
            genbtcblocks 1 0

            RANDOM_WAIT=$(echo $[$RANDOM % 3 + 1])
            sleeptraced "$RANDOM_WAIT"
        fi
    done
}

waitfortradepaymentsent() {
    # Loops until buyer's trade payment has been sent.
    PORT="$1"
    SELLER="$2"
    OFFER_ID="$3"
    DONE=0
    while : ; do
        if [ "$DONE" -ne 0 ]; then
            break
        fi

        printdate "$SELLER:  Looking at trade with id $OFFER_ID."
        CMD="$CLI_BASE --port=$PORT gettrade --trade-id=$OFFER_ID"
        printdate "$SELLER CLI: $CMD"
        GETTRADE_CMD_OUTPUT=$(gettrade "$CMD")
        exitoncommandalert $?
        echo "$GETTRADE_CMD_OUTPUT"
        printbreak

        TRADE_DETAIL=$(gettradedetail "$GETTRADE_CMD_OUTPUT")
        exitoncommandalert $?

        IS_TRADE_PAYMENT_SENT=$(istradepaymentsent "$TRADE_DETAIL")
        exitoncommandalert $?
        printdate "$SELLER:  Has buyer's payment been initiated?  $IS_TRADE_PAYMENT_SENT"
        if [ "$IS_TRADE_PAYMENT_SENT" = "YES" ]
        then
            DONE=1
        else
            RANDOM_WAIT=$(echo $[$RANDOM % 3 + 1])
            sleeptraced "$RANDOM_WAIT"
        fi
        printbreak
    done
}

waitfortradepaymentreceived() {
    # Loops until buyer's trade payment has been received.
    PORT="$1"
    SELLER="$2"
    OFFER_ID="$3"
    DONE=0
    while : ; do
        if [ "$DONE" -ne 0 ]; then
            break
        fi

        printdate "$SELLER:  Looking at trade with id $OFFER_ID."
        CMD="$CLI_BASE --port=$PORT gettrade --trade-id=$OFFER_ID"
        printdate "$SELLER CLI: $CMD"
        GETTRADE_CMD_OUTPUT=$(gettrade "$CMD")
        exitoncommandalert $?
        echo "$GETTRADE_CMD_OUTPUT"
        printbreak

        TRADE_DETAIL=$(gettradedetail "$GETTRADE_CMD_OUTPUT")
        exitoncommandalert $?

        # When the seller receives a 'payment sent' message, it is assumed funds (fiat) have already been deposited.
        # In a real trade, there is usually a delay between receipt of a 'payment sent' message, and the funds deposit,
        # but we do not need to simulate that in this regtest script.
        IS_TRADE_PAYMENT_SENT=$(istradepaymentreceived "$TRADE_DETAIL")
        exitoncommandalert $?
        printdate "$SELLER:  Has buyer's payment been transferred to seller's account?  $IS_TRADE_PAYMENT_SENT"
        if [ "$IS_TRADE_PAYMENT_SENT" = "YES" ]
        then
            DONE=1
        else
            RANDOM_WAIT=$(echo $[$RANDOM % 3 + 1])
            sleeptraced "$RANDOM_WAIT"
        fi
        printbreak
    done
}

delayconfirmpaymentstarted() {
    # Confirm payment started after a random delay.  This should be run in the background
    # while the payee polls the trade status, waiting for the message before confirming
    # payment has been received.
    PAYER="$1"
    PORT="$2"
    OFFER_ID="$3"
    RANDOM_WAIT=$(echo $[$RANDOM % 5 + 1])
    printdate "$PAYER:  Sending 'payment sent' message to seller in $RANDOM_WAIT seconds..."
    sleeptraced "$RANDOM_WAIT"
    CMD="$CLI_BASE --port=$PORT confirmpaymentstarted --trade-id=$OFFER_ID"
    printdate "$PAYER_CLI: $CMD"
    SENT_MSG=$($CMD)
    commandalert $? "Could not send confirmpaymentstarted message."
    # Print the confirmpaymentstarted command's console output.
    printdate "$SENT_MSG"
    printbreak
}

delayconfirmpaymentreceived() {
    # Confirm payment received after a random delay.  This should be run in the background
    # while the payer polls the trade status, waiting for the confirmation from the seller
    # that funds have been received.
    PAYEE="$1"
    PORT="$2"
    OFFER_ID="$3"
    RANDOM_WAIT=$(echo $[$RANDOM % 5 + 1])
    printdate "$PAYEE:  Sending 'payment sent' message to seller in $RANDOM_WAIT seconds..."
    sleeptraced "$RANDOM_WAIT"
    CMD="$CLI_BASE --port=$PORT confirmpaymentreceived --trade-id=$OFFER_ID"
    printdate "$PAYEE_CLI: $CMD"
    RCVD_MSG=$($CMD)
    commandalert $? "Could not send confirmpaymentstarted message."
    # Print the confirmpaymentstarted command's console output.
    printdate "$RCVD_MSG"
    printbreak
}

# This is a large function that might be split into smaller functions.  But we are not testing
# api methods here, just demonstrating how to use them to get through the V1 trade protocol with
# the CLI.  It should work for any trade between Bob & Alice, as long as Alice is maker, Bob is
# taker, and the offer to be taken is the first displayed in Bob's getoffers command output.
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
    CMD="$CLI_BASE --port=$BOB_PORT takeoffer --offer-id=$OFFER_ID --payment-account=$BOB_ACCT_ID --fee-currency=BSQ"
    printdate "BOB CLI: $CMD"
    TRADE=$($CMD)
    commandalert $? "Could not take offer."
    # Print the takeoffer command's console output.
    printdate "$TRADE"
    printbreak

    waitfortradedepositpublished "$OFFER_ID"
    waitfortradedepositconfirmed "$OFFER_ID"

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

    # Asynchronously send a confirm payment started message after a random delay.
    delayconfirmpaymentstarted "$PAYER" "$PAYER_PORT" "$OFFER_ID" &

    if [ "$DIRECTION" = "BUY" ]
    then
        # Bob waits for payment, polling status in taker specific trade detail.
        waitfortradepaymentsent "$PAYEE_PORT" "$PAYEE" "$OFFER_ID"
    else
        # Alice waits for payment, polling status in maker specific trade detail.
        waitfortradepaymentsent "$PAYEE_PORT" "$PAYEE" "$OFFER_ID"
    fi


    # Asynchronously send a confirm payment received message after a random delay.
    delayconfirmpaymentreceived "$PAYEE" "$PAYEE_PORT" "$OFFER_ID" &

    if [ "$DIRECTION" = "BUY" ]
    then
        # Alice waits for payment rcvd confirm from Bob, polling status in maker specific trade detail.
        waitfortradepaymentreceived "$PAYER_PORT" "$PAYER" "$OFFER_ID"
    else
        # Bob waits for payment rcvd confirm from Alice, polling status in taker specific trade detail.
        waitfortradepaymentreceived "$PAYER_PORT" "$PAYER" "$OFFER_ID"
    fi

    # Generate some btc blocks
    printdate "Generating btc blocks after payment."
    genbtcblocks 2 2
    printbreak

    # Complete the trade on both sides
    printdate "BOB $BOB_ROLE:  Closing trade and keeping funds in Bisq wallet."
    CMD="$CLI_BASE --port=$BOB_PORT closetrade --trade-id=$OFFER_ID"
    printdate "BOB CLI: $CMD"
    KEEP_FUNDS_MSG=$($CMD)
    commandalert $? "Closed trade with closetrade command."
    # Print the closetrade command's console output.
    printdate "$KEEP_FUNDS_MSG"
    sleeptraced 3
    printbreak

    printdate "ALICE (taker):  Closing trade and keeping funds in Bisq wallet."
    CMD="$CLI_BASE --port=$ALICE_PORT closetrade --trade-id=$OFFER_ID"
    printdate "ALICE CLI: $CMD"
    KEEP_FUNDS_MSG=$($CMD)
    commandalert $? "Closed trade with closetrade command."
    # Print the closetrade command's console output.
    printdate "$KEEP_FUNDS_MSG"
    sleeptraced 3
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
