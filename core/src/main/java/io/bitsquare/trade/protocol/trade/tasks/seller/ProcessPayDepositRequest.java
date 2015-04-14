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
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class ProcessPayDepositRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessPayDepositRequest.class);

    public ProcessPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            PayDepositRequest message = (PayDepositRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            processModel.tradingPeer.setConnectedOutputsForAllInputs(checkNotNull(message.buyerConnectedOutputsForAllInputs));
            checkArgument(message.buyerConnectedOutputsForAllInputs.size() > 0);
            processModel.tradingPeer.setOutputs(checkNotNull(message.buyerOutputs));
            processModel.tradingPeer.setTradeWalletPubKey(checkNotNull(message.buyerTradeWalletPubKey));
            processModel.tradingPeer.setPubKeyRing(checkNotNull(message.buyerPubKeyRing));
            processModel.tradingPeer.setFiatAccount(checkNotNull(message.buyerFiatAccount));
            processModel.tradingPeer.setAccountId(nonEmptyStringOf(message.buyerAccountId));
            trade.setTradeAmount(positiveCoinOf(nonZeroCoinOf(message.tradeAmount)));

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}