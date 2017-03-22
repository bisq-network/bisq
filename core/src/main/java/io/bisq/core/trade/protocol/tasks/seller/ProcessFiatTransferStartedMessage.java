/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.seller;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.core.util.Validator;
import io.bisq.protobuffer.message.trade.FiatTransferStartedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class ProcessFiatTransferStartedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessFiatTransferStartedMessage.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public ProcessFiatTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            FiatTransferStartedMessage message = (FiatTransferStartedMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            processModel.tradingPeer.setPayoutAddressString(Validator.nonEmptyStringOf(message.buyerPayoutAddress));

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            removeMailboxMessageAfterProcessing();

            trade.setState(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}