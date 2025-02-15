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

package bisq.core.trade.protocol.bisq_v5.tasks.seller;

import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.messages.PreparedTxBuyerSignaturesMessage;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerProcessPreparedTxBuyerSignaturesMessage extends TradeTask {
    public SellerProcessPreparedTxBuyerSignaturesMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            PreparedTxBuyerSignaturesMessage message = (PreparedTxBuyerSignaturesMessage) processModel.getTradeMessage();
            checkNotNull(message);
            checkTradeId(processModel.getOfferId(), message);

            // If the seller is the taker, we already have the warning & redirect buyer signatures, so just ignore them.
            // Also, the deposit tx was already set earlier in the task SellerAsTakerSignsDepositTx, in that case.
            if (trade instanceof SellerAsMakerTrade) {
                processModel.getTradePeer().setWarningTxBuyerSignature(message.getBuyersWarningTxBuyerSignature());
                processModel.setWarningTxBuyerSignature(message.getSellersWarningTxBuyerSignature());
                processModel.getTradePeer().setRedirectTxBuyerSignature(message.getBuyersRedirectTxBuyerSignature());
                processModel.setRedirectTxBuyerSignature(message.getSellersRedirectTxBuyerSignature());
                processModel.setDepositTx(processModel.getBtcWalletService().getTxFromSerializedTx(processModel.getPreparedDepositTx()));
            }

            // The deposit tx is finalized by adding all the buyer witnesses.
            processModel.getTradeWalletService().sellerAddsBuyerWitnessesToDepositTx(
                    processModel.getDepositTx(),
                    processModel.getBtcWalletService().getTxFromSerializedTx(message.getDepositTxWithBuyerWitnesses())
            );

            // TODO: takerFeeTxTd:
            // When we receive that message the taker has published the taker fee, so we apply it to the trade.
            // The takerFeeTx was sent in the first message. It should be part of DelayedPayoutTxSignatureRequest
            // but that cannot be changed due backward compatibility issues. It is a left over from the old trade protocol.
            trade.setTakerFeeTxId(processModel.getTakeOfferFeeTxId());

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
