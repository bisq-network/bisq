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

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.DaoSetupService;
import bisq.core.dao.governance.proposal.TxException;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.locale.CurrencyUtil;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AssetService implements PersistedDataHost, DaoSetupService, DaoStateListener {
    private final Storage<RemovedAssetsList> storage;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final WalletsManager walletsManager;
    private final DaoStateService daoStateService;

    @Getter
    private final RemovedAssetsList removedAssetsList = new RemovedAssetsList();
    @Getter
    private final ObservableList<StatefulAsset> statefulAssets = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AssetService(Storage<RemovedAssetsList> storage,
                        BsqWalletService bsqWalletService,
                        BtcWalletService btcWalletService,
                        WalletsManager walletsManager,
                        DaoStateService daoStateService) {
        this.storage = storage;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.walletsManager = walletsManager;
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        //TODO why not use dao state?
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            RemovedAssetsList persisted = storage.initAndGetPersisted(removedAssetsList, 100);
            if (persisted != null) {
                removedAssetsList.clear();
                removedAssetsList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
        daoStateService.addBsqStateListener(this);
    }

    @Override
    public void start() {
        updateList();
    }

    public void updateList() {
        statefulAssets.setAll(CurrencyUtil.getAssetStream()
                .map(StatefulAsset::new)
                .peek(statefulAsset -> {
                    terminateAssetIfRemovedByVoting(statefulAsset);
                    findFeePayment(statefulAsset).ifPresent(statefulAsset::addFeePayment);
                })
                .sorted(StatefulAsset::compareTo)
                .collect(Collectors.toList()));
    }

    private Optional<StatefulAsset.FeePayment> findFeePayment(StatefulAsset statefulAsset) {
        return findFeeTx(statefulAsset)
                .map(tx -> {
                    String txId = tx.getId();
                    long burntFee = tx.getBurntFee();
                    return new StatefulAsset.FeePayment(txId, burntFee);
                });
    }

    private Optional<Tx> findFeeTx(StatefulAsset statefulAsset) {
        return daoStateService.getAssetListingFeeOpReturnTxOutputs().stream()
                .filter(txOutput -> {
                    byte[] hash = AssetConsensus.getHash(statefulAsset);
                    byte[] opReturnData = AssetConsensus.getOpReturnData(hash);
                    return Arrays.equals(opReturnData, txOutput.getOpReturnData());
                })
                .map(txOutput -> daoStateService.getTx(txOutput.getTxId()).orElse(null))
                .filter(Objects::nonNull)
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        updateList();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addToRemovedAssetsListByVoting(String tickerSymbol) {
        log.info("Asset '{}' was removed by DAO voting", CurrencyUtil.getNameAndCode(tickerSymbol));
        removedAssetsList.add(new RemovedAsset(tickerSymbol, RemoveReason.VOTING));
        persist();

        statefulAssets.stream().filter(statefulAsset -> statefulAsset.getTickerSymbol().equals(tickerSymbol))
                .findAny().ifPresent(this::terminateAssetIfRemovedByVoting);
    }

    public boolean hasPaidBSQFee(String tickerSymbol) {
        //TODO
        return false;
    }


    public boolean isAssetRemoved(String tickerSymbol) {
        boolean isRemoved = removedAssetsList.getList().stream()
                .anyMatch(removedAsset -> removedAsset.getTickerSymbol().equals(tickerSymbol));
        if (isRemoved)
            log.info("Asset '{}' was removed", CurrencyUtil.getNameAndCode(tickerSymbol));

        return isRemoved;
    }

    public boolean isAssetRemovedByVoting1(String tickerSymbol) {
        boolean isRemoved = getRemovedAssetsByRemoveReason(RemoveReason.VOTING).stream()
                .anyMatch(removedAsset -> removedAsset.getTickerSymbol().equals(tickerSymbol));
        if (isRemoved)
            log.info("Asset '{}' was removed by DAO voting", CurrencyUtil.getNameAndCode(tickerSymbol));

        return isRemoved;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<RemovedAsset> getRemovedAssetsByRemoveReason(RemoveReason removeReason) {
        return removedAssetsList.getList().stream()
                .filter(e -> e.getRemoveReason() == removeReason)
                .collect(Collectors.toList());
    }

    private void terminateAssetIfRemovedByVoting(StatefulAsset statefulAsset) {
        if (isAssetRemoved(statefulAsset.getTickerSymbol()))
            statefulAsset.terminate();
    }

    private void persist() {
        storage.queueUpForSave(20);
    }

    public Transaction payFee(StatefulAsset statefulAsset, long listingFee) throws InsufficientMoneyException, TxException {
        checkArgument(!statefulAsset.wasTerminated(), "Asset must not have been removed");
        checkArgument(listingFee >= getFeePerDay().value, "Fee must not be less then listing fee for 1 day.");
        checkArgument(listingFee % 100 == 0, "Fee must be a multiple of 1 BSQ (100 satoshi).");
        try {
            // We create a prepared Bsq Tx for the listing fee.
            final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(Coin.valueOf(listingFee));
            byte[] hash = AssetConsensus.getHash(statefulAsset);
            byte[] opReturnData = AssetConsensus.getOpReturnData(hash);
            // We add the BTC inputs for the miner fee.
            final Transaction txWithBtcFee = btcWalletService.completePreparedBurnBsqTx(preparedBurnFeeTx, opReturnData);
            // We sign the BSQ inputs of the final tx.
            Transaction transaction = bsqWalletService.signTx(txWithBtcFee);
            log.info("Asset listing fee tx: " + transaction);
            return transaction;
        } catch (WalletException | TransactionVerificationException e) {
            throw new TxException(e);
        }
    }

    public Coin getFeePerDay() {
        return AssetConsensus.getFeePerDay(daoStateService, daoStateService.getChainHeight());
    }

    // Broadcast tx and publish proposal to P2P network
    public void publishTransaction(StatefulAsset statefulAsset, Transaction transaction, long listingFee, ResultHandler resultHandler,
                                   ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("Asset listing fee tx has been published. TxId={}", transaction.getHashAsString());
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });

        statefulAsset.addFeePayment(new StatefulAsset.FeePayment(transaction.getHashAsString(), listingFee));
    }
}
