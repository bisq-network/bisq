/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.seller.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class TakerProcessFiatTransferStartedMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerProcessFiatTransferStartedMessage.class);

    public TakerProcessFiatTransferStartedMessage(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            FiatTransferStartedMessage message = (FiatTransferStartedMessage) takerTradeProcessModel.getTradeMessage();
            checkTradeId(takerTradeProcessModel.getId(), message);
            checkNotNull(message);

            takerTradeProcessModel.tradingPeer.setSignature(checkNotNull(message.buyerSignature));
            takerTradeProcessModel.tradingPeer.setPayoutAmount(positiveCoinOf(nonZeroCoinOf(message.buyerPayoutAmount)));
            takerTradeProcessModel.setPayoutAmount(positiveCoinOf(nonZeroCoinOf(message.sellerPayoutAmount)));
            takerTradeProcessModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(message.buyerPayoutAddress));

            if (takerTrade instanceof TakerAsBuyerTrade)
                takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.FIAT_PAYMENT_STARTED);
            else if (takerTrade instanceof TakerAsSellerTrade)
                takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.FIAT_PAYMENT_STARTED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}