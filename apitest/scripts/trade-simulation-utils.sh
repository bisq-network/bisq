#! /bin/bash

# This file must be sourced by the main driver.

source "${APITEST_SCRIPTS_HOME}/trade-simulation-env.sh"

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
    CMD="${CLI_BASE} --port=${ARBITRATOR_PORT} registerdisputeagent --dispute-agent-type=mediator --registration-key=${REG_KEY}"
    SILENT=$($CMD)
    commandalert $? "Could not register dev/test mediator."
    CMD="${CLI_BASE} --port=${ARBITRATOR_PORT} registerdisputeagent --dispute-agent-type=refundagent --registration-key=${REG_KEY}"
    SILENT=$($CMD)
    commandalert $? "Could not register dev/test refundagent."
    # Do something with $SILENT to keep codacy happy.
    echo "$SILENT"  > /dev/null
}

getbtcoreaddress() {
    CMD="bitcoin-cli -regtest  -rpcport=19443 -rpcuser=apitest -rpcpassword=apitest getnewaddress"
    NEW_ADDRESS=$($CMD)
    echo "${NEW_ADDRESS}"
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
        printdate "Block Hash #$i:${NEW_BLOCK_HASH}"
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
    echo "${NEW_STRING}"
}

printbalances() {
    PORT="$1"
    printcmd "${CLI_BASE} --port=${PORT} getbalance"
    $CLI_BASE --port="$PORT" getbalance
}

getpaymentaccountmethods() {
    CMD="$1"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get payment method ids."
    printdate "Payment Method IDs:"
    echo "${CMD_OUTPUT}"
}

getpaymentaccountform() {
    CMD="$1"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get new payment account form."
    echo "${CMD_OUTPUT}"
}

editpaymentaccountform() {
    COUNTRY_CODE="$1"
    CMD="python3 ${APITEST_SCRIPTS_HOME}/editf2faccountform.py $COUNTRY_CODE"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not edit payment account form."
    printdate "Saved payment account form as ${F2F_ACCT_FORM}."
}

getnewpaymentacctid() {
    CREATE_PAYMENT_ACCT_OUTPUT="$1"
    PAYMENT_ACCT_DETAIL=$(echo -e "${CREATE_PAYMENT_ACCT_OUTPUT}" | sed -n '3p')
    ACCT_ID=$(echo -e "$PAYMENT_ACCT_DETAIL" | awk '{print $NF}')
    echo "${ACCT_ID}"
}

getnewpaymentacctcurrency() {
    CREATE_PAYMENT_ACCT_OUTPUT="$1"
    PAYMENT_ACCT_DETAIL=$(echo -e "${CREATE_PAYMENT_ACCT_OUTPUT}" | sed -n '3p')
    # This is brittle; it requires the account name field to have N words,
    # e.g, "Face to Face Payment Account" as defined in editf2faccountform.py.
    CURRENCY_CODE=$(echo -e "$PAYMENT_ACCT_DETAIL" | awk '{print $6}')
    echo "${CURRENCY_CODE}"
}

createpaymentacct() {
    CMD="$1"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not create new payment account."
    echo "${CMD_OUTPUT}"
}

getpaymentaccounts() {
    PORT="$1"
    printcmd "${CLI_BASE} --port=${PORT} getpaymentaccts"
    CMD="$CLI_BASE --port=$PORT getpaymentaccts"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get payment accounts."
    echo "${CMD_OUTPUT}"
}

createoffer() {
    CREATE_OFFER_CMD="$1"
    OFFER_DESC=$($CREATE_OFFER_CMD)

    # If the CLI command exited with an error, print the CLI error, and
    # return from this function now, passing the error status code to the caller.
    commandalert $? "Could not create offer."

    OFFER_DETAIL=$(echo -e "${OFFER_DESC}" | sed -n '2p')
    NEW_OFFER_ID=$(echo -e "${OFFER_DETAIL}" | awk '{print $NF}')
    echo "${NEW_OFFER_ID}"
}

getcurrentprice() {
    PORT="$1"
    CURRENCY_CODE="$2"
    CMD="$CLI_BASE --port=$PORT getbtcprice --currency-code=$CURRENCY_CODE"
    CMD_OUTPUT=$($CMD)
    commandalert $? "Could not get current market $CURRENCY_CODE price."
    FLOOR=$(echo $CMD_OUTPUT| cut -d'.' -f 1)
    commandalert $? "Could not get the floor of the current market $CURRENCY_CODE price."
    INTEGER=$(echo $FLOOR | tr -cd '[[:digit:]]')
    commandalert $? "Could not convert the current market $CURRENCY_CODE price string to an integer."
    echo "$INTEGER"
}
