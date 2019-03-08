#!/bin/bash

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
SETUP_SCRIPT=${SCRIPT_DIR}/setup.sh
source ${SETUP_SCRIPT}

ARGS=""

if [ ! -z "$BASE_CURRENCY_NETWORK" ]; then
    ARGS="$ARGS --baseCurrencyNetwork=$BASE_CURRENCY_NETWORK"
fi
if [ ! -z "$MAX_CONNECTIONS" ]; then
    ARGS="$ARGS --maxConnections=$MAX_CONNECTIONS"
fi
if [ ! -z "$NODE_PORT" ]; then
    ARGS="$ARGS --nodePort=$NODE_PORT"
fi
if [ ! -z "$APP_NAME" ]; then
    ARGS="$ARGS --appName=$APP_NAME"
fi
if [ ! -z "$SEED_NODES" ]; then
    ARGS="$ARGS --seedNodes=$SEED_NODES"
fi
if [ ! -z "$BTC_NODES" ]; then
    ARGS="$ARGS --btcNodes=$BTC_NODES"
fi
if [ ! -z "$USE_LOCALHOST_FOR_P2P" ]; then
    ARGS="$ARGS --useLocalhostForP2P=$USE_LOCALHOST_FOR_P2P"
fi

JAVA_OPTS='-Xms1800m -Xmx1800m' ./build/app/bin/bisq-seednode $ARGS
