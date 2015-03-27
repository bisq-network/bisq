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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessFiatTransferStartedMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessFiatTransferStartedMessage.class);

    public ProcessFiatTransferStartedMessage(TaskRunner taskHandler, TakerTrade model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(takerTradeProcessModel.id, takerTradeProcessModel.getTradeMessage());
            FiatTransferStartedMessage message = (FiatTransferStartedMessage) takerTradeProcessModel.getTradeMessage();

            takerTradeProcessModel.offerer.signature = checkNotNull(message.offererSignature);
            takerTradeProcessModel.offerer.payoutAmount = positiveCoinOf(nonZeroCoinOf(message.offererPayoutAmount));
            takerTradeProcessModel.taker.payoutAmount = positiveCoinOf(nonZeroCoinOf(message.takerPayoutAmount));
            takerTradeProcessModel.offerer.payoutAddressString = nonEmptyStringOf(message.offererPayoutAddress);
            takerTrade.setProcessState(TakerTrade.TakerProcessState.FIAT_PAYMENT_STARTED);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}