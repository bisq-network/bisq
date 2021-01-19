#! /bin/bash

# This file must be sourced by the main driver.

export CLI_BASE="./bisq-cli --password=xyz"
export ARBITRATOR_PORT=9997
export ALICE_PORT=9998
export BOB_PORT=9999
export F2F_ACCT_FORM="f2f-acct.json"

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
        echo "Usage: $0 [-d buy|sell] [-c <country-code>] [-f <fixed-price> || -m <margin-from-price>] [-a <amount in btc>]" 1>&2
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
                export COUNTRY_CODE=${c}
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


parselimitorderopts() {
    usage() {
        echo "Usage: $0 [-l limit-price] [-d buy|sell] [-c <country-code>] [-f <fixed-price> || -m <margin-from-price>] [-a <amount in btc>]" 1>&2
        exit 1;
    }

    local OPTIND o l d c f m a
    while getopts "l:d:c:f:m:a:" o; do
        case "${o}" in
            l) l=${OPTARG}
                export LIMIT_PRICE=${l}
                ;;
            d) d=$(echo "${OPTARG}" | tr '[:lower:]' '[:upper:]')
                ((d == "BUY" || d == "SELL")) || usage
                export DIRECTION=${d}
                ;;
            c) c=$(echo "${OPTARG}"| tr '[:lower:]' '[:upper:]')
                export COUNTRY_CODE=${c}
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

    if [ -z "${l}" ]; then
        usage
    fi

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

printscriptparams() {
    if [ -n "${LIMIT_PRICE+1}" ]; then
        echo "	LIMIT_PRICE = ${LIMIT_PRICE}"
    fi

    echo "	DIRECTION = ${DIRECTION}"
    echo "	COUNTRY_CODE = ${COUNTRY_CODE}"
    echo "	FIXED_PRICE = ${FIXED_PRICE}"
    echo "	MKT_PRICE_MARGIN = ${MKT_PRICE_MARGIN}"
    echo "	AMOUNT = ${AMOUNT}"

    if [ -n "${BOB_ROLE+1}" ]; then
        echo "	BOB_ROLE = ${BOB_ROLE}"
    fi

    if [ -n "${ALICE_ROLE+1}" ]; then
        echo "	ALICE_ROLE = ${ALICE_ROLE}"
    fi
}
