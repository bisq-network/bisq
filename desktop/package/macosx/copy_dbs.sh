#!/bin/bash

cd $(dirname $0)/../../../

# Mainnet

dbDir=~/Library/Application\ Support/Bisq/btc_mainnet/db
resDir=p2p/src/main/resources

cp "$dbDir/TradeStatistics2Store" "$resDir/TradeStatistics2Store_BTC_MAINNET"
cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_BTC_MAINNET"

# Testnet
dbDir=~/Library/Application\ Support/Bisq/btc_dao_testnet/db

cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_BTC_DAO_REGTEST"
cp "$dbDir/BlindVoteStore" "$resDir/BlindVoteStore_BTC_DAO_REGTEST"
cp "$dbDir/DaoStateStore" "$resDir/DaoStateStore_BTC_DAO_REGTEST"
cp "$dbDir/ProposalStore" "$resDir/ProposalStore_BTC_DAO_REGTEST"
cp "$dbDir/TempProposalStore" "$resDir/TempProposalStore_BTC_DAO_REGTEST"
cp "$dbDir/TradeStatistics2Store" "$resDir/TradeStatistics2Store_BTC_DAO_REGTEST"
