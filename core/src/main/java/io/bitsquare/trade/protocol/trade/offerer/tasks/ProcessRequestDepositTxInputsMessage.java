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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessRequestDepositTxInputsMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessRequestDepositTxInputsMessage.class);

    public ProcessRequestDepositTxInputsMessage(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            checkTradeId(offererTradeProcessModel.id, offererTradeProcessModel.getTradeMessage());
            RequestDepositTxInputsMessage requestDepositTxInputsMessage = (RequestDepositTxInputsMessage) offererTradeProcessModel.getTradeMessage();

            offererTrade.setTradeAmount(positiveCoinOf(nonZeroCoinOf(requestDepositTxInputsMessage.tradeAmount)));
            offererTradeProcessModel.setTakeOfferFeeTxId(nonEmptyStringOf(requestDepositTxInputsMessage.takeOfferFeeTxId));
            offererTradeProcessModel.taker.tradeWalletPubKey = checkNotNull(requestDepositTxInputsMessage.takerTradeWalletPubKey);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
            failed(t);
        }
    }
}