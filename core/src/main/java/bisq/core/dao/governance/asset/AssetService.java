/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.governance.asset;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.EvaluatedProposal;
import bisq.core.dao.state.model.governance.RemoveAssetProposal;
import bisq.core.locale.CurrencyUtil;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AssetService implements DaoSetupService, DaoStateListener {
    private static final long DEFAULT_LOOK_BACK_PERIOD = 120; // 120 days

    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final DaoStateService daoStateService;
    private final BsqFormatter bsqFormatter;

    // Only accessed via getter which fills the list on demand
    private final List<StatefulAsset> lazyLoadedStatefulAssets = new ArrayList<>();
    private long bsqFeePerDay;
    private long minVolumeInBtc;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AssetService(BsqWalletService bsqWalletService,
                        BtcWalletService btcWalletService,
                        WalletsManager walletsManager,
                        TradeStatisticsManager tradeStatisticsManager,
                        DaoStateService daoStateService,
                        BsqFormatter bsqFormatter) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.daoStateService = daoStateService;
        this.bsqFormatter = bsqFormatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addDaoStateListener(this);
    }

    @Override
    @SuppressWarnings({"EmptyMethod"})
    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        int chainHeight = daoStateService.getChainHeight();
        bsqFeePerDay = daoStateService.getParamValueAsCoin(Param.ASSET_LISTING_FEE_PER_DAY, chainHeight).value;
        minVolumeInBtc = daoStateService.getParamValueAsCoin(Param.ASSET_MIN_VOLUME, chainHeight).value;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<StatefulAsset> getStatefulAssets() {
        if (lazyLoadedStatefulAssets.isEmpty()) {
            lazyLoadedStatefulAssets.addAll(CurrencyUtil.getSortedAssetStream()
                    .filter(asset -> !asset.getTickerSymbol().equals("BSQ"))
                    .map(StatefulAsset::new)
                    .collect(Collectors.toList()));
        }
        return lazyLoadedStatefulAssets;
    }

    // Call takes bout 22 ms. Should be only called on demand (e.g. view is showing the data)
    public void updateAssetStates() {
        // For performance optimisation we map the trade stats to a temporary lookup map and convert it to a custom
        // TradeAmountDateTuple object holding only the data we need.
        Map<String, List<TradeAmountDateTuple>> lookupMap = new HashMap<>();
        tradeStatisticsManager.getObservableTradeStatisticsSet().stream()
                .filter(e -> CurrencyUtil.isCryptoCurrency(e.getCurrency()))
                .forEach(e -> {
                    lookupMap.putIfAbsent(e.getCurrency(), new ArrayList<>());
                    lookupMap.get(e.getCurrency()).add(new TradeAmountDateTuple(e.getAmount(), e.getDateAsLong()));
                });

        getStatefulAssets().stream()
                .filter(e -> AssetState.REMOVED_BY_VOTING != e.getAssetState()) // if once set to REMOVED_BY_VOTING we ignore it for further processing
                .forEach(statefulAsset -> {
                    AssetState assetState;
                    String tickerSymbol = statefulAsset.getTickerSymbol();
                    if (wasAssetRemovedByVoting(tickerSymbol)) {
                        assetState = AssetState.REMOVED_BY_VOTING;
                    } else {
                        statefulAsset.setFeePayments(getFeePayments(statefulAsset));
                        long lookBackPeriodInDays = getLookBackPeriodInDays(statefulAsset);
                        statefulAsset.setLookBackPeriodInDays(lookBackPeriodInDays);
                        long lookupDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(lookBackPeriodInDays);
                        long tradeVolume = getTradeVolume(lookupDate, lookupMap.get(tickerSymbol));
                        statefulAsset.setTradeVolume(tradeVolume);
                        if (isInTrialPeriod(statefulAsset)) {
                            assetState = AssetState.IN_TRIAL_PERIOD;
                        } else if (tradeVolume >= minVolumeInBtc) {
                            assetState = AssetState.ACTIVELY_TRADED;
                        } else {
                            assetState = AssetState.DE_LISTED;
                        }
                    }
                    statefulAsset.setAssetState(assetState);
                });

        lookupMap.clear();
    }

    public boolean isActive(String tickerSymbol) {
        return DevEnv.isDaoActivated() ? findAsset(tickerSymbol).map(StatefulAsset::isActive).orElse(false) : true;
    }

    public Transaction payFee(StatefulAsset statefulAsset,
                              long listingFee) throws InsufficientMoneyException, TxException {
        checkArgument(!statefulAsset.wasRemovedByVoting(), "Asset must not have been removed");
        checkArgument(listingFee >= getFeePerDay().value, "Fee must not be less then listing fee for 1 day.");
        checkArgument(listingFee % 100 == 0, "Fee must be a multiple of 1 BSQ (100 satoshi).");
        try {
            // We create a prepared Bsq Tx for the listing fee.
            Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTxForAssetListing(Coin.valueOf(listingFee));
            byte[] hash = AssetConsensus.getHash(statefulAsset);
            byte[] opReturnData = AssetConsensus.getOpReturnData(hash);
            // We add the BTC inputs for the miner fee.
            Transaction txWithBtcFee = btcWalletService.completePreparedBurnBsqTx(preparedBurnFeeTx, opReturnData);
            // We sign the BSQ inputs of the final tx.
            Transaction transaction = bsqWalletService.signTxAndVerifyNoDustOutputs(txWithBtcFee);
            log.info("Asset listing fee tx: " + transaction);
            return transaction;
        } catch (WalletException | TransactionVerificationException e) {
            throw new TxException(e);
        }
    }

    public Coin getFeePerDay() {
        return AssetConsensus.getFeePerDay(daoStateService, daoStateService.getChainHeight());
    }

    public void publishTransaction(Transaction transaction, ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, TxType.ASSET_LISTING_FEE, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("Asset listing fee tx has been published. TxId={}", transaction.getTxId().toString());
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Get the trade volume from lookupDate until current date
    private long getTradeVolume(long lookupDate, @Nullable List<TradeAmountDateTuple> tradeAmountDateTupleList) {
        if (tradeAmountDateTupleList == null) {
            // Was never traded
            return 0;
        }

        return tradeAmountDateTupleList.stream()
                .filter(e -> e.getTradeDate() > lookupDate)
                .mapToLong(TradeAmountDateTuple::getTradeAmount)
                .sum();
    }

    private boolean isInTrialPeriod(StatefulAsset statefulAsset) {
        for (FeePayment feePayment : statefulAsset.getFeePayments()) {
            Optional<Integer> passedDays = feePayment.getPassedDays(daoStateService);
            if (passedDays.isPresent()) {
                long daysCoveredByFee = feePayment.daysCoveredByFee(bsqFeePerDay);
                if (daysCoveredByFee >= passedDays.get()) {
                    return true;
                }
            }
        }
        return false;
    }


    @NotNull
    private Long getLookBackPeriodInDays(StatefulAsset statefulAsset) {
        // We need to use the block height of the fee payment tx not the current one as feePerDay might have been
        // changed in the meantime.
        long bsqFeePerDay = statefulAsset.getLastFeePayment()
                .flatMap(feePayment -> daoStateService.getTx(feePayment.getTxId()))
                .map(tx -> daoStateService.getParamValueAsCoin(Param.ASSET_LISTING_FEE_PER_DAY, tx.getBlockHeight()).value)
                .orElse(bsqFormatter.parseParamValueToCoin(Param.ASSET_LISTING_FEE_PER_DAY, Param.ASSET_LISTING_FEE_PER_DAY.getDefaultValue()).value);

        return statefulAsset.getLastFeePayment()
                .map(feePayment -> feePayment.daysCoveredByFee(bsqFeePerDay))
                .orElse(DEFAULT_LOOK_BACK_PERIOD);
    }

    private List<FeePayment> getFeePayments(StatefulAsset statefulAsset) {
        return getFeeTxs(statefulAsset).stream()
                .map(tx -> {
                    String txId = tx.getId();
                    long burntFee = tx.getBurntFee();
                    return new FeePayment(txId, burntFee);
                })
                .collect(Collectors.toList());
    }

    private List<Tx> getFeeTxs(StatefulAsset statefulAsset) {
        return daoStateService.getAssetListingFeeOpReturnTxOutputs().stream()
                .filter(txOutput -> {
                    byte[] hash = AssetConsensus.getHash(statefulAsset);
                    byte[] opReturnData = AssetConsensus.getOpReturnData(hash);
                    return Arrays.equals(opReturnData, txOutput.getOpReturnData());
                })
                .map(txOutput -> daoStateService.getTx(txOutput.getTxId()).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(BaseTx::getTime))
                .collect(Collectors.toList());
    }

    private Optional<StatefulAsset> findAsset(String tickerSymbol) {
        return getStatefulAssets().stream().filter(e -> e.getTickerSymbol().equals(tickerSymbol)).findAny();
    }

    private boolean wasAssetRemovedByVoting(String tickerSymbol) {
        boolean isRemoved = getAcceptedRemoveAssetProposalStream()
                .anyMatch(proposal -> proposal.getTickerSymbol().equals(tickerSymbol));
        if (isRemoved)
            log.info("Asset '{}' was removed", CurrencyUtil.getNameAndCode(tickerSymbol));

        return isRemoved;
    }

    private Stream<RemoveAssetProposal> getAcceptedRemoveAssetProposalStream() {
        return daoStateService.getEvaluatedProposalList().stream()
                .filter(evaluatedProposal -> evaluatedProposal.getProposal() instanceof RemoveAssetProposal)
                .filter(EvaluatedProposal::isAccepted)
                .map(e -> ((RemoveAssetProposal) e.getProposal()));
    }

    @Value
    private static final class TradeAmountDateTuple {
        private final long tradeAmount;
        private final long tradeDate;

        TradeAmountDateTuple(long tradeAmount, long tradeDate) {
            this.tradeAmount = tradeAmount;
            this.tradeDate = tradeDate;
        }
    }
}
