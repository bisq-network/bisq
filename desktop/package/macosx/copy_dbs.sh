#!/bin/bash

cd $(dirname $0)/../../../

# Set BISQ_DIR as environment var to the path of your locally synced Bisq data directory e.g. BISQ_DIR=~/Library/Application\ Support/Bisq

dbDir=$BISQ_DIR/btc_mainnet/db
resDir=p2p/src/main/resources

cp "$dbDir/TradeStatistics2Store" "$resDir/TradeStatistics2Store_BTC_MAINNET"
cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_BTC_MAINNET"
cp "$dbDir/DaoStateStore" "$resDir/DaoStateStore_BTC_MAINNET"
cp "$dbDir/SignedWitnessStore" "$resDir/SignedWitnessStore_BTC_MAINNET"
