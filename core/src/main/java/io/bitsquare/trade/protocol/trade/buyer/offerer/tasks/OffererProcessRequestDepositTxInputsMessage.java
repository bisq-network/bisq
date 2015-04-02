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

package io.bitsquare.trade.protocol.trade.buyer.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class OffererProcessRequestDepositTxInputsMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererProcessRequestDepositTxInputsMessage.class);

    public OffererProcessRequestDepositTxInputsMessage(TaskRunner taskHandler, Trade offererTrade) {
        super(taskHandler, offererTrade);
    }

    @Override
    protected void doRun() {
        try {
            RequestDepositTxInputsMessage message = (RequestDepositTxInputsMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            offererTrade.setTradeAmount(positiveCoinOf(nonZeroCoinOf(message.tradeAmount)));
            processModel.setTakeOfferFeeTxId(nonEmptyStringOf(message.takeOfferFeeTxId));
            processModel.tradingPeer.setTradeWalletPubKey(checkNotNull(message.takerTradeWalletPubKey));

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);

            if (offererTrade instanceof OffererAsBuyerTrade)
                offererTrade.setLifeCycleState(OffererAsBuyerTrade.LifeCycleState.OFFER_OPEN);
            else if (offererTrade instanceof OffererAsSellerTrade)
                offererTrade.setLifeCycleState(OffererAsSellerTrade.LifeCycleState.OFFER_OPEN);

            failed(t);
        }
    }
}