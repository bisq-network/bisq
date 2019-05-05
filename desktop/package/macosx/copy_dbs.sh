#!/bin/bash

cd $(dirname $0)/../../../

# Mainnet

dbDir=~/Library/Application\ Support/Bisq/btc_mainnet/db
resDir=p2p/src/main/resources

cp "$dbDir/TradeStatistics2Store" "$resDir/TradeStatistics2Store_BTC_MAINNET"
cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_BTC_MAINNET"
cp "$dbDir/DaoStateStore" "$resDir/DaoStateStore_BTC_MAINNET"
cp "$dbDir/TempProposalStore" "$resDir/TempProposalStore_BTC_MAINNET"
