#!/bin/bash

cd $(dirname $0)/../../../

# Mainnet

dbDir=~/Library/Application\ Support/Bisq/btc_mainnet/db
resDir=p2p/src/main/resources

cp "$dbDir/TradeStatistics2Store" "$resDir/TradeStatistics2Store_BTC_MAINNET"
cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_BTC_MAINNET"

# Testnet
dbDir=~/Library/Application\ Support/bisq-BTC_PRODTEST/btc_testnet/db

cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_BTC_TESTNET"
cp "$dbDir/BlindVoteStore" "$resDir/BlindVoteStore_BTC_TESTNET"
cp "$dbDir/DaoStateStore" "$resDir/DaoStateStore_BTC_TESTNET"
cp "$dbDir/ProposalStore" "$resDir/ProposalStore_BTC_TESTNET"
cp "$dbDir/TempProposalStore" "$resDir/TempProposalStore_BTC_TESTNET"
