#!/bin/bash

cd $(dirname $0)/../../../

version="1.9.19"

# Set BISQ_DIR as environment var to the path of your locally synced Bisq data directory e.g. BISQ_DIR=~/Library/Application\ Support/Bisq

dbDir=$BISQ_DIR/btc_mainnet/db
resDir=p2p/src/main/resources

# Only commit new TradeStatistics3Store if you plan to add it to
# https://github.com/bisq-network/bisq/blob/0345c795e2c227d827a1f239a323dda1250f4e69/common/src/main/java/bisq/common/app/Version.java#L40 as well.
cp "$dbDir/TradeStatistics3Store" "$resDir/TradeStatistics3Store_${version}_BTC_MAINNET"
cp "$dbDir/AccountAgeWitnessStore" "$resDir/AccountAgeWitnessStore_${version}_BTC_MAINNET"
cp "$dbDir/DaoStateStore" "$resDir/DaoStateStore_BTC_MAINNET"
cp "$dbDir/SignedWitnessStore" "$resDir/SignedWitnessStore_BTC_MAINNET"
cp "$dbDir/BurningManAccountingStore" "$resDir/BurningManAccountingStore_BTC_MAINNET"
cp -a "$dbDir/BsqBlocks/." "$resDir/BsqBlocks_BTC_MAINNET/"

# Only to be updated when required
# cp "$dbDir/ProposalStore" "$resDir/ProposalStore_BTC_MAINNET"
# cp "$dbDir/TempProposalStore" "$resDir/TempProposalStore_BTC_MAINNET"
# cp "$dbDir/BlindVoteStore" "$resDir/BlindVoteStore_BTC_MAINNET"
