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

package bisq.core.trade.protocol.bsqswap.tasks.seller;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerSetupTxListener extends BsqSwapTask {

    private AddressConfidenceListener confidenceListener;

    public SellerSetupTxListener(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (bsqSwapTrade.isCompleted()) {
                complete();
                return;
            }

            BsqWalletService walletService = bsqSwapProtocolModel.getBsqWalletService();
            //todo we do not have a unique address like in normal trades...
            // we need another approach -> use txId
            Address address = Address.fromString(walletService.getParams(), bsqSwapProtocolModel.getBsqAddress());

            TransactionConfidence confidence = walletService.getConfidenceForAddress(address);
            if (isInNetwork(confidence)) {
                applyConfidence(confidence);
            } else {
                confidenceListener = new AddressConfidenceListener(address) {
                    @Override
                    public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                        if (isInNetwork(confidence))
                            applyConfidence(confidence);
                    }
                };
                walletService.addAddressConfidenceListener(confidenceListener);
            }

            // we complete immediately, our object stays alive because the balanceListener is stored in the WalletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void applyConfidence(TransactionConfidence confidence) {
        if (bsqSwapTrade.getTransaction() != null) {
            return;
        }

        Transaction walletTx = bsqSwapProtocolModel.getBsqWalletService().getTransaction(confidence.getTransactionHash());
        bsqSwapTrade.applyTransaction(walletTx);
        bsqSwapProtocolModel.getTradeManager().requestPersistence();
        log.error("payoutTx received from network {}", walletTx);

        bsqSwapTrade.setState(BsqSwapTrade.State.COMPLETED);
        bsqSwapProtocolModel.getTradeManager().onTradeCompleted(bsqSwapTrade);

        UserThread.execute(() -> {
            if (confidenceListener != null)
                bsqSwapProtocolModel.getBtcWalletService().removeAddressConfidenceListener(confidenceListener);
        });
    }

    private boolean isInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }
}
