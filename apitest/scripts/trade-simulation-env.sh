#! /bin/bash

# This file must be sourced by the main driver.

export CLI_BASE="./bisq-cli --password=xyz"
export ARBITRATOR_PORT=9997
export ALICE_PORT=9998
export BOB_PORT=9999
export F2F_ACCT_FORM="f2f-acct.json"

checkos() {
    LINUX=FALSE
    DARWIN=FALSE
    UNAME=$(uname)
    case "$UNAME" in
      Linux* )
        export LINUX=TRUE
        ;;
      Darwin* )
        export DARWIN=TRUE
        ;;
    esac
    if [[ "$LINUX" == "TRUE" ]]; then
        printdate "Running on supported Linux OS."
    elif [[ "$DARWIN" == "TRUE" ]]; then
        printdate "Running on supported Mac OS."
    else
        printdate "Script cannot run on $OSTYPE OS, only Linux and OSX are supported."
        exit 1
    fi
}

checksetup() {
    checkos

    apitestusage() {
        echo "The apitest harness must be running a local bitcoin regtest node, a seednode, an arbitration node,"
        echo "Bob & Alice daemons, and bitcoin-core's bitcoin-cli must be in the system PATH."
        echo ""
        echo "From the project's root dir, start all supporting nodes from a terminal:"
        echo "./bisq-apitest --apiPassword=xyz --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon  --shutdownAfterTests=false"
        exit 1;
    }
    printdate "Checking $APP_HOME for some expected directories and files."
    if [ -d "$APP_HOME/apitest" ]; then
        printdate "Subproject apitest exists.";
    else
        printdate "Error:  Subproject apitest not found, maybe because you are not running the script from the project root dir."
        exit 1
    fi
    if [ -f "$APP_HOME/bisq-cli" ]; then
        printdate "The bisq-cli script exists.";
    else
        printdate "Error:  The bisq-cli script not found, maybe because you are not running the script from the project root dir."
        exit 1
    fi
    printdate "Checking to see local bitcoind is running, and bitcoin-cli is in PATH."
    checkbitcoindrunning
    checkbitcoincliinpath
    printdate "Checking to see bisq servers are running."
    checkseednoderunning
    checkarbnoderunning
    checkalicenoderunning
    checkbobnoderunning
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
        echo "Usage: $0 [-l limit-price] [-d buy|sell] [-c <country-code>] [-f <fixed-price> || -m <margin-from-price>] [-a <amount in btc>] [-w <price-poll-interval(s)>]" 1>&2
        exit 1;
    }

    local OPTIND o l d c f m a w
    while getopts "l:d:c:f:m:a:w:" o; do
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
            w) w=${OPTARG}
               export WAIT=${w}
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

    if [ -z "${w}" ]; then
        WAIT=120
    elif [ "$w" -lt 20 ]; then
        printdate "The -w <price-poll-interval(s)> option is too low, minimum allowed is 20s.  Using default 120s."
        WAIT=120
    fi
}

parsebsqswaporderopts() {
    usage() {
        echo "Usage: $0 [-d buy|sell] [-f <fixed-price>] [-a <amount in btc>]" 1>&2
        exit 1;
    }

    local OPTIND o d f a
    while getopts "d:f:a:" o; do
        case "${o}" in
            d) d=$(echo "${OPTARG}" | tr '[:lower:]' '[:upper:]')
                ((d == "BUY" || d == "SELL")) || usage
                export DIRECTION=${d}
                ;;
            f) f=${OPTARG}
               export FIXED_PRICE=${f}
               ;;
            a) a=${OPTARG}
               export AMOUNT=${a}
               ;;
            *) usage ;;
        esac
    done
    shift $((OPTIND-1))

    if [ -z "${d}" ] || [ -z "${a}" ]; then
        usage
    fi

    if [ -z "${f}" ] ; then
        usage
    fi

    export CURRENCY_CODE="BSQ"
}

parsexmrscriptopts() {
    usage() {
        echo "Usage: $0 [-d buy|sell] [-f <fixed-price> || -m <margin-from-price>] [-a <amount in btc>]" 1>&2
        exit 1;
    }

    local OPTIND o d f m a
    while getopts "d:f:m:a:" o; do
        case "${o}" in
            d) d=$(echo "${OPTARG}" | tr '[:lower:]' '[:upper:]')
                ((d == "BUY" || d == "SELL")) || usage
                export DIRECTION=${d}
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

    if [ -z "${d}" ] ||  [ -z "${a}" ]; then
        usage
    fi

    if [ -z "${f}" ] && [ -z "${m}" ]; then
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

checkbitcoindrunning() {
    # There may be a '+' char in the path and we have to escape it for pgrep.
    if [[ $APP_HOME == *"+"* ]]; then
        ESCAPED_APP_HOME=$(escapepluschar "$APP_HOME")
    else
        ESCAPED_APP_HOME="$APP_HOME"
    fi
    if pgrep -f "bitcoind -datadir=$ESCAPED_APP_HOME/apitest/build/resources/main/Bitcoin-regtest" > /dev/null ; then
        printdate "The regtest bitcoind node is running on host."
    else
        printdate "Error:  regtest bitcoind node is not running on host, exiting."
        apitestusage
    fi
}

checkbitcoincliinpath() {
    if which bitcoin-cli > /dev/null ; then
        printdate "The bitcoin-cli binary is in the system PATH."
    else
        printdate "Error:  bitcoin-cli binary is not in the system PATH, exiting."
        apitestusage
    fi
}

checkseednoderunning() {
    if [[ "$LINUX" == "TRUE" ]]; then
        if pgrep -f "bisq.seednode.SeedNodeMain" > /dev/null ; then
            printdate "The seed node is running on host."
        else
            printdate "Error:  seed node is not running on host, exiting."
            apitestusage
        fi
    elif [[ "$DARWIN" == "TRUE" ]]; then
        if ps -A | awk '/[S]eedNodeMain/ {print $1}' > /dev/null ; then
            printdate "The seednode is running on host."
        else
            printdate "Error:  seed node is not running on host, exiting."
            apitestusage
        fi
    else
        printdate "Error:  seed node is not running on host, exiting."
        apitestusage
    fi
}

checkarbnoderunning() {
    if [[ "$LINUX" == "TRUE" ]]; then
        if pgrep -f "bisq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Arb_dao" > /dev/null ; then
            printdate "The arbitration node is running on host."
        else
            printdate "Error:  arbitration node is not running on host, exiting."
            apitestusage
        fi
    elif [[ "$DARWIN" == "TRUE" ]]; then
        if ps -A | awk '/[b]isq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Arb_dao/ {print $1}' > /dev/null ; then
            printdate "The arbitration node is running on host."
        else
            printdate "Error:  arbitration node is not running on host, exiting."
            apitestusage
        fi
    else
        printdate "Error:  arbitration node is not running on host, exiting."
        apitestusage
    fi
}

checkalicenoderunning() {
    if [[ "$LINUX" == "TRUE" ]]; then
        if pgrep -f "bisq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Alice_dao" > /dev/null ; then
            printdate "Alice's node is running on host."
        else
            printdate "Error:  Alice's node is not running on host, exiting."
            apitestusage
        fi
    elif [[ "$DARWIN" == "TRUE" ]]; then
        if ps -A | awk '/[b]isq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Alice_dao/ {print $1}' > /dev/null ; then
            printdate "Alice's node node is running on host."
        else
            printdate "Error:  Alice's node is not running on host, exiting."
            apitestusage
        fi
    else
        printdate "Error:  Alice's node is not running on host, exiting."
        apitestusage
    fi
}

checkbobnoderunning() {
    if [[ "$LINUX" == "TRUE" ]]; then
        if pgrep -f "bisq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Alice_dao" > /dev/null ; then
            printdate "Bob's node is running on host."
        else
            printdate "Error:  Bob's node is not running on host, exiting."
            apitestusage
        fi
    elif [[ "$DARWIN" == "TRUE" ]]; then
        if ps -A | awk '/[b]isq.daemon.app.BisqDaemonMain --appName=bisq-BTC_REGTEST_Alice_dao/ {print $1}' > /dev/null ; then
            printdate "Bob's node node is running on host."
        else
            printdate "Error:  Bob's node is not running on host, exiting."
            apitestusage
        fi
    else
        printdate "Error:  Bob's node is not running on host, exiting."
        apitestusage
    fi
}

printscriptparams() {
    if [ -n "${LIMIT_PRICE+1}" ]; then
        echo "	LIMIT_PRICE = $LIMIT_PRICE"
    fi

    echo "	DIRECTION = $DIRECTION"
    echo "	COUNTRY_CODE = $COUNTRY_CODE"
    echo "	FIXED_PRICE = $FIXED_PRICE"
    echo "	MKT_PRICE_MARGIN = $MKT_PRICE_MARGIN"
    echo "	AMOUNT = $AMOUNT"

    if [ -n "${BOB_ROLE+1}" ]; then
        echo "	BOB_ROLE = $BOB_ROLE"
    fi

    if [ -n "${ALICE_ROLE+1}" ]; then
        echo "	ALICE_ROLE = $ALICE_ROLE"
    fi

    if [ -n "${WAIT+1}" ]; then
        echo "	WAIT = $WAIT"
    fi
}

printbsqswapscriptparams() {
    echo "	DIRECTION = $DIRECTION"
    echo "	FIXED_PRICE = $FIXED_PRICE"
    echo "	AMOUNT = $AMOUNT"
}
