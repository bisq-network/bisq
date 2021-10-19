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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BuyerSetupDepositTxListener extends TradeTask {
    // Use instance fields to not get eaten up by the GC
    private Subscription tradeStateSubscription;
    private AddressConfidenceListener confidenceListener;

    public BuyerSetupDepositTxListener(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (trade.getDepositTx() == null && processModel.getPreparedDepositTx() != null) {
                BtcWalletService walletService = processModel.getBtcWalletService();
                NetworkParameters params = walletService.getParams();
                Transaction preparedDepositTx = new Transaction(params, processModel.getPreparedDepositTx());
                checkArgument(!preparedDepositTx.getOutputs().isEmpty(), "preparedDepositTx.getOutputs() must not be empty");
                Address depositTxAddress = preparedDepositTx.getOutput(0).getScriptPubKey().getToAddress(params);

                // For buyer as maker takerFeeTxId is null
                @Nullable String takerFeeTxId = trade.getTakerFeeTxId();
                String makerFeeTxId = trade.getOffer().getOfferFeePaymentTxId();
                TransactionConfidence confidence = walletService.getConfidenceForAddress(depositTxAddress);
                if (isConfTxDepositTx(confidence, params, depositTxAddress, takerFeeTxId, makerFeeTxId) &&
                        isVisibleInNetwork(confidence)) {
                    applyConfidence(confidence);
                } else {
                    confidenceListener = new AddressConfidenceListener(depositTxAddress) {
                        @Override
                        public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                            if (isConfTxDepositTx(confidence, params, depositTxAddress,
                                    takerFeeTxId, makerFeeTxId) && isVisibleInNetwork(confidence)) {
                                applyConfidence(confidence);
                            }
                        }
                    };
                    walletService.addAddressConfidenceListener(confidenceListener);

                    tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                        if (trade.isDepositPublished()) {
                            swapReservedForTradeEntry();

                            // hack to remove tradeStateSubscription at callback
                            UserThread.execute(this::unSubscribeAndRemoveListener);
                        }
                    });
                }
            }

            // we complete immediately, our object stays alive because the balanceListener is stored in the WalletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    // We check if the txIds of the inputs matches our maker fee tx and taker fee tx and if the depositTxAddress we
    // use for the confidence lookup is use as an output address.
    // This prevents that past txs which have the our depositTxAddress as input or output (deposit or payout txs) could
    // be interpreted as our deposit tx. This happened because if a bug which caused re-use of the Multisig address
    // entries and if both traders use the same key for multiple trades the depositTxAddress would be the same.
    // We fix that bug as well but we also need to avoid that past already used addresses might be taken again
    // (the Multisig flag got reverted to available in the address entry).
    private boolean isConfTxDepositTx(@Nullable TransactionConfidence confidence,
                                      NetworkParameters params,
                                      Address depositTxAddress,
                                      @Nullable String takerFeeTxId,
                                      String makerFeeTxId) {
        if (confidence == null) {
            return false;
        }

        Transaction walletTx = processModel.getTradeWalletService().getWalletTx(confidence.getTransactionHash());
        long numInputMatches = walletTx.getInputs().stream()
                .map(TransactionInput::getOutpoint)
                .filter(Objects::nonNull)
                .map(TransactionOutPoint::getHash)
                .map(Sha256Hash::toString)
                .filter(txId -> txId.equals(takerFeeTxId) || txId.equals(makerFeeTxId))
                .count();
        if (takerFeeTxId == null && numInputMatches != 1) {
            log.warn("We got a transactionConfidenceTx which does not match our inputs. " +
                            "takerFeeTxId is null (valid if role is buyer as maker) and numInputMatches " +
                            "is not 1 as expected (for makerFeeTxId). " +
                            "numInputMatches={}, transactionConfidenceTx={}",
                    numInputMatches, walletTx);
            return false;
        } else if (takerFeeTxId != null && numInputMatches != 2) {
            log.warn("We got a transactionConfidenceTx which does not match our inputs. " +
                            "numInputMatches is not 2 as expected (for makerFeeTxId and takerFeeTxId). " +
                            "numInputMatches={}, transactionConfidenceTx={}",
                    numInputMatches, walletTx);
            return false;
        }

        boolean isOutputMatching = walletTx.getOutputs().stream()
                .map(transactionOutput -> transactionOutput.getScriptPubKey().getToAddress(params))
                .anyMatch(address -> address.equals(depositTxAddress));
        if (!isOutputMatching) {
            log.warn("We got a transactionConfidenceTx which does not has the depositTxAddress " +
                            "as output (but as input). depositTxAddress={}, transactionConfidenceTx={}",
                    depositTxAddress, walletTx);
        }
        return isOutputMatching;
    }

    private void applyConfidence(TransactionConfidence confidence) {
        if (trade.getDepositTx() == null) {
            Transaction walletTx = processModel.getTradeWalletService().getWalletTx(confidence.getTransactionHash());
            trade.applyDepositTx(walletTx);
            BtcWalletService.printTx("depositTx received from network", walletTx);

            // We don't want to trigger the tradeStateSubscription when setting the state, so we unsubscribe before
            unSubscribeAndRemoveListener();
            trade.setState(Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK);

            processModel.getTradeManager().requestPersistence();
        } else {
            unSubscribeAndRemoveListener();
        }

        swapReservedForTradeEntry();
    }

    private boolean isVisibleInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }

    private void swapReservedForTradeEntry() {
        processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(),
                AddressEntry.Context.RESERVED_FOR_TRADE);
    }

    private void unSubscribeAndRemoveListener() {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            tradeStateSubscription = null;
        }

        if (confidenceListener != null) {
            processModel.getBtcWalletService().removeAddressConfidenceListener(confidenceListener);
            confidenceListener = null;
        }
    }
}
