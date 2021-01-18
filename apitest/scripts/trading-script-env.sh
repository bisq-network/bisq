#! /bin/bash

# This file must be sourced by the main driver.

export CLI_BASE="./bisq-cli --password=xyz"
export ALICE_PORT=9998
export BOB_PORT=9999

printdate() {
	echo "[$(date)]  $@"
}

printdate "Started ${APP_HOME}/${APP_BASE_NAME}."

checksetup() {
    apitestusage() {
        echo "The apitest harness must be running a local bitcoin regtest node, a seednode, arbitration node, and Bob & Alice daemons."
        echo ""
        echo "From the project's root dir, start all supporting nodes from a terminal:"
        echo "./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon  --shutdownAfterTests=false"
        echo ""
        echo "Register dispute agents in the arbitration daemon after it initializes."
        echo "./bisq-cli --password=xyz --port=9997 registerdisputeagent --dispute-agent-type=mediator \
            --registration-key=6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a"
        echo "./bisq-cli --password=xyz --port=9997 registerdisputeagent --dispute-agent-type=refundagent \
            --registration-key=6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a"
        exit 1;
    }
    printdate "Checking ${APP_HOME} for some expected directories and files."
    if [ -d "${APP_HOME}/apitest" ]; then
        printdate "Subproject apitest exists.";
    else
        printdate "Error:  Subproject apitest not found, maybe because you are not running the script from the project root dir."
        exit 1
    fi
    if [ -f "${APP_HOME}/bisq-cli" ]; then
        printdate "The bisq-cli script exists.";
    else
        printdate "Error:  The bisq-cli script not found, maybe because you are not running the script from the project root dir."
        exit 1
    fi
    printdate "Checking to see local bitcoind is running."
    checkbitcoindrunning
    printdate "Checking to see bisq servers are running."
    if pgrep -f "bisq.seednode.SeedNodeMain" > /dev/null ; then
        printdate "The seednode is running on host."
    else
        printdate "Error:  seed is not running on host, exiting."
        apitestusage
    fi
    if pgrep -f "bisq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Arb_dao" > /dev/null ; then
        printdate "The arbitration node is running on host."
    else
        printdate "Error:  arbitration node is not running on host, exiting."
        apitestusage
    fi
    if pgrep -f "bisq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Alice_dao" > /dev/null ; then
        printdate "Alice's daemon node is running on host."
    else
        printdate "Error:  Alice's daemon node is not running on host, exiting."
        apitestusage
    fi
    if pgrep -f "bisq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Bob_dao" > /dev/null ; then
        printdate "Bob's daemon node is running on host."
    else
        printdate "Error:  Bob's daemon node is not running on host, exiting."
        apitestusage
    fi
}

parseopts() {
	usage() {
		echo "Usage: $0 [-d buy|sell] [-c <currency-code>] [-f <fixed-price> || -m <margin-from-price>] [-a <amount in btc>]" 1>&2
		exit 1;
	}

	local OPTIND o d c f m a
	while getopts "d:c:f:m:a:" o; do
		case "${o}" in
		    d) d=$(echo "${OPTARG}" | tr '[:lower:]' '[:upper:]')
		        ((d == "BUY" || d == "SELL")) || usage
		        export DIRECTION=${d}
		        ;;
		    c) c=$(echo "${OPTARG}"| tr '[:lower:]' '[:upper:]')
		        export CURRENCY_CODE=${c}
		    	;;
		    f) f=${OPTARG}
			   export FIXED_PRICE=${f}
		       ;;
		    m) m=${OPTARG}
			   export MKT_PRICE_MARGIN=${m}
		       ;;
		    a) a=${OPTARG}
		       export AMOUNT=${a}
		       ;;
		    *) usage ;;
		esac
	done
	shift $((OPTIND-1))

	if [ -z "${d}" ] || [ -z "${c}" ] || [ -z "${a}" ]; then
		usage
	fi

	if [ -z "${f}" ] && [ -z "${m}" ]; then
		usage
	fi

	if [ -n "${f}" ] && [ -n "${m}" ]; then
		printdate "Must use margin-from-price param (-m) or fixed-price param (-f), not both."
		usage
	fi

    if [ "$DIRECTION" = "SELL" ]
    then
        export BOB_ROLE="(taker/buyer)"
        export ALICE_ROLE="(maker/seller)"
    else
        export BOB_ROLE="(taker/seller)"
        export ALICE_ROLE="(maker/buyer)"
    fi
}

printscriptparams() {
	echo "	DIRECTION = ${DIRECTION}"
	echo "	CURRENCY_CODE = ${CURRENCY_CODE}"
	echo "	FIXED_PRICE = ${FIXED_PRICE}"
	echo "	MKT_PRICE_MARGIN = ${MKT_PRICE_MARGIN}"
	echo "	AMOUNT = ${AMOUNT}"
	echo "	BOB_ROLE = ${BOB_ROLE}"
	echo "	ALICE_ROLE = ${ALICE_ROLE}"
}

checkbitcoindrunning() {
    # There may be a '+' char in the path and we have to escape it for pgrep.
    if [[ ${APP_HOME} == *"+"* ]]; then
        ESCAPED_APP_HOME=$(escapepluschar "${APP_HOME}")
    else
        ESCAPED_APP_HOME="${APP_HOME}"
    fi
    if pgrep -f "bitcoind -datadir=${ESCAPED_APP_HOME}/apitest/build/resources/main/Bitcoin-regtest" > /dev/null ; then
        printdate "The regtest bitcoind node is running on host."
    else
        printdate "Error:  regtest bitcoind node is not running on host, exiting."
        apitestusage
    fi
}

registerdisputeagents() {
    # Silently register dev dispute agents.  It's easy to forget.
    REG_KEY="6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a"
    SILENT=$(./bisq-cli --password=xyz --port=9997 registerdisputeagent --dispute-agent-type=mediator --registration-key=${REG_KEY})
    SILENT=$(./bisq-cli --password=xyz --port=9997 registerdisputeagent --dispute-agent-type=refundagent --registration-key=${REG_KEY})
    # Do something with $SILENT to keep codacy happy.
    echo "$SILENT"  > /dev/null
}


printbreak() {
	echo ""
	echo ""
}

printcmd() {
	echo -en "$@\n"
}

printdate_sameline() {
	echo -n "[$(date)]  $@ "
}

sleeptraced() {
  PERIOD=$1
  printdate "sleeping for $PERIOD"
  sleep "$PERIOD"
}

printbalances() {
    PORT=$1
    printcmd "${CLI_BASE} --port=${PORT} getbalance"
    $CLI_BASE --port="$PORT" getbalance
}

getpaymentaccts() {
    PORT=$1
    printcmd "${CLI_BASE} --port=${PORT} getpaymentaccts"
    $CLI_BASE --port="$PORT" getpaymentaccts
}

getdummyacctid() {
    PORT=$1
    PAYMENT_ACCTS=$(${CLI_BASE} --port="$PORT" getpaymentaccts)
    DUMMY_ACCT_1=$(echo -e "${PAYMENT_ACCTS}" | sed -n '2p')
    DUMMY_ACCT_2=$(echo -e "${PAYMENT_ACCTS}" | sed -n '3p')
    if [[ "$DUMMY_ACCT_1=" == *"PerfectMoney dummy"* ]]; then
        DUMMY_ACCT=$DUMMY_ACCT_1
    else
        DUMMY_ACCT=$DUMMY_ACCT_2
    fi
    ACCT_ID=$(echo -e "$DUMMY_ACCT" | awk '{print $NF}')
    echo "${ACCT_ID}"
}

createoffer() {
    CREATE_OFFER_CMD=$1
    OFFER_DESC=$($CREATE_OFFER_CMD)
    if [[ "$OFFER_DESC" != "Buy/Sell"* ]]; then
        echo "=========================================================="
        echo "Error: ${OFFER_DESC}"
        echo "=========================================================="
    else
        OFFER_DETAIL=$(echo -e "${OFFER_DESC}" | sed -n '2p')
        NEW_OFFER_ID=$(echo -e "${OFFER_DETAIL}" | awk '{print $NF}')
        echo "${NEW_OFFER_ID}"
    fi
}

getbtcoreaddress() {
    CMD="bitcoin-cli -regtest  -rpcport=19443 -rpcuser=apitest -rpcpassword=apitest getnewaddress"
    NEW_ADDRESS=$(${CMD})
    echo "${NEW_ADDRESS}"
}

genbtcblocks() {
	NUM_BLOCKS=$1
	SECONDS_BETWEEN_BLOCKS=$2
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
    CMD=$1
    NEW_BLOCK_HASH=$(${CMD} | sed -n '2p')
    echo "$NEW_BLOCK_HASH"
}

escapepluschar() {
    STRING=$1
    NEW_STRING=$(echo "${STRING//+/\\+}")
    echo "${NEW_STRING}"
}

# Keep this in case there is a need to ready user input from stdin.
readYesOrNo() {
	question=$1
	echo -n "$question  Yes or No: "
	read answer
	answer=$(echo "$answer" | tr [a-z] [A-Z])
	if [ "$answer" = "Y" ]
	then
		echo You answered yes: "$answer"
	else
		echo You answered no: "$answer"
	fi
	return 0
}
