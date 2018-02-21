#!/bin/bash

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
SETUP_SCRIPT=$SCRIPT_DIR/setup.sh
source $SETUP_SCRIPT

ARGS="--maxConnections=$MAX_CONNECTIONS --baseCurrencyNetwork=$BASE_CURRENCY_NETWORK --nodePort=$NODE_PORT --appName=$APP_NAME"

if [ ! -z "$SEED_NODES" ]; then
    ARGS="$ARGS --seedNodes=$SEED_NODES"
fi
if [ ! -z "$BTC_NODES" ]; then
    ARGS="$ARGS --btcNodes=$BTC_NODES"
fi
if [ ! -z "$USE_LOCALHOST_FOR_P2P" ]; then
    ARGS="$ARGS --useLocalhostForP2P=$USE_LOCALHOST_FOR_P2P"
fi
if [ ! -z "$MY_ADDRESS" ]; then
    ARGS="$ARGS --myAddress=${MY_ADDRESS}"
elif [ ! -z "$ONION_ADDRESS" ]; then
    ARGS="$ARGS --myAddress=${ONION_ADDRESS}.onion:$NODE_PORT"
fi

echo java -Xms1800m -Xmx1800m -jar ./target/SeedNode.jar $ARGS
java -Xms1800m -Xmx1800m -jar ./target/SeedNode.jar $ARGS

#TODO validate mandatory params
