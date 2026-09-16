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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.SendBsqSwapMessageTask;

import bisq.common.taskrunner.TaskRunner;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class SendFinalizedTxMessage extends SendBsqSwapMessageTask {

    @SuppressWarnings({"unused"})
    public SendFinalizedTxMessage(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            byte[] transactionBytes = Objects.requireNonNull(protocolModel.getTx()).clone();
            BsqSwapFinalizedTxMessage message = new BsqSwapFinalizedTxMessage(
                    protocolModel.getOfferId(),
                    protocolModel.getMyNodeAddress(),
                    transactionBytes);

            checkArgument(protocolModel.getDaoFacade().isDaoStateReadyAndInSync() ||
                            protocolModel.hasTransactionPublication(transactionBytes),
                    "DAO state is not ready and in sync and transaction publication handoff is unknown");
            send(message);
        } catch (Throwable t) {
            failed(t);
        }
    }
}
