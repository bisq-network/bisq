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

package bisq.core.trade.protocol.bisq_v1.tasks.seller_as_maker;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxMessage;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsMakerProcessDepositTxMessage extends TradeTask {
    public SellerAsMakerProcessDepositTxMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            DepositTxMessage message = (DepositTxMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkNotNull(message);

            processModel.getTradePeer().setPreparedDepositTx(checkNotNull(message.getDepositTxWithoutWitnesses()));
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            // When we receive that message the taker has published the taker fee, so we apply it to the trade.
            // The takerFeeTx was sent in the first message. It should be part of DelayedPayoutTxSignatureRequest
            // but that cannot be changed due backward compatibility issues. It is a left over from the old trade protocol.
            trade.setTakerFeeTxId(processModel.getTakeOfferFeeTxId());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
