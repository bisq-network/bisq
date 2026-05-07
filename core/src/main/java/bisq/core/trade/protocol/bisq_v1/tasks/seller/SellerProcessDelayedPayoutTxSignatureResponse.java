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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.DelayedPayoutTxSignatureResponse;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.TradeValidation.checkDerEncodedEcdsaSignature;
import static bisq.core.trade.validation.TradeValidation.checkSerializedTransaction;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerProcessDelayedPayoutTxSignatureResponse extends TradeTask {
    public SellerProcessDelayedPayoutTxSignatureResponse(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            DelayedPayoutTxSignatureResponse response = (DelayedPayoutTxSignatureResponse) processModel.getTradeMessage();
            checkNotNull(response);

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            TradingPeer tradePeer = processModel.getTradePeer();

            byte[] delayedPayoutTxBuyerSignature = checkDerEncodedEcdsaSignature(response.getDelayedPayoutTxBuyerSignature());
            tradePeer.setDelayedPayoutTxSignature(delayedPayoutTxBuyerSignature);

            byte[] depositTx = checkSerializedTransaction(response.getDepositTx(), btcWalletService);
            Transaction buyersDepositTxWithWitnesses = btcWalletService.getTxFromSerializedTx(depositTx);
            Transaction myDepositTx = processModel.getDepositTx();
            tradeWalletService.sellerAddsBuyerWitnessesToDepositTx(myDepositTx, buyersDepositTxWithWitnesses);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
