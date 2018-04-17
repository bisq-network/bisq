#!/bin/bash

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
SETUP_SCRIPT=${SCRIPT_DIR}/setup.sh
source ${SETUP_SCRIPT}

IP=`ip -4 -o addr show eth0  | sed 's/.*inet \([0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\).*/\1/'`
ARGS="--myAddress $IP:${NODE_PORT:-9999}"
if [ ! -z "$APP_NAME" ]; then
    ARGS="$ARGS --appName=$APP_NAME"
fi
if [ ! -z "$NODE_PORT" ]; then
    ARGS="$ARGS --nodePort=$NODE_PORT"
fi
if [ ! -z "$USE_LOCALHOST_FOR_P2P" ]; then
    ARGS="$ARGS --useLocalhostForP2P=$USE_LOCALHOST_FOR_P2P"
fi
if [ ! -z "$SEED_NODES" ]; then
    ARGS="$ARGS --seedNodes=$SEED_NODES"
fi
if [ ! -z "$BTC_NODES" ]; then
    ARGS="$ARGS --btcNodes=$BTC_NODES"
fi
if [ ! -z "$BITCOIN_REGTEST_HOST" ]; then
    ARGS="$ARGS --bitcoinRegtestHost=$BITCOIN_REGTEST_HOST"
fi
if [ ! -z "$BASE_CURRENCY_NETWORK" ]; then
    ARGS="$ARGS --baseCurrencyNetwork=$BASE_CURRENCY_NETWORK"
fi
if [ ! -z "$LOG_LEVEL" ]; then
    ARGS="$ARGS --logLevel=$LOG_LEVEL"
fi
if [ ! -z "$USE_DEV_PRIVILEGE_KEYS" ]; then
    ARGS="$ARGS --useDevPrivilegeKeys=$USE_DEV_PRIVILEGE_KEYS"
fi

echo mvn exec:java -Dexec.mainClass="io.bisq.api.app.ApiMain" -Dexec.args="$ARGS"
mvn exec:java -Dexec.mainClass="io.bisq.api.app.ApiMain" -Dexec.args="$ARGS"
