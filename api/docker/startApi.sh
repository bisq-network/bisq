#!/bin/bash

ARGS=""
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
if [ "$ENABLE_HTTP_API_EXPERIMENTAL_FEATURES" == "true" ]; then
    ARGS="$ARGS --enableHttpApiExperimentalFeatures"
fi
if [ ! -z "$HTTP_API_PORT" ]; then
    ARGS="$ARGS --httpApiPort=$HTTP_API_PORT"
fi
if [ ! -z "$HTTP_API_HOST" ]; then
    ARGS="$ARGS --httpApiHost=$HTTP_API_HOST"
fi

echo ../gradlew run --no-daemon --args "foo $ARGS"
../gradlew run --no-daemon --args "foo $ARGS"
