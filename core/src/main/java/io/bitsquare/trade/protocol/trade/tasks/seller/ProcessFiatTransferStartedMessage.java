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

package io.bitsquare.trade.protocol.trade.tasks.seller;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessFiatTransferStartedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessFiatTransferStartedMessage.class);

    public ProcessFiatTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            FiatTransferStartedMessage message = (FiatTransferStartedMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(message.buyerPayoutAddress));

            trade.setTradeState(TradeState.SellerState.FIAT_PAYMENT_STARTED_MSG_RECEIVED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();

            failed(t);
        }
    }
}