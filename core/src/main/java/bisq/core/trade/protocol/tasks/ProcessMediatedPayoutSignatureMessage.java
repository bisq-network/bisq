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

package bisq.core.trade.protocol.tasks;

import bisq.core.trade.Trade;
import bisq.core.trade.messages.MediatedPayoutSignatureMessage;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class ProcessMediatedPayoutSignatureMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public ProcessMediatedPayoutSignatureMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            MediatedPayoutSignatureMessage message = (MediatedPayoutSignatureMessage) processModel.getTradeMessage();
            Validator.checkTradeId(processModel.getOfferId(), message);
            checkNotNull(message);

            processModel.getTradingPeer().setTxSignatureFromMediation(checkNotNull(message.getTxSignature()));

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());
            processModel.removeMailboxMessageAfterProcessing(trade);

            trade.setDisputeState(Trade.DisputeState.MEDIATION_TX_SIG_RECEIVED);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
