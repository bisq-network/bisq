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

package io.bitsquare.trade.protocol.trade.tasks.buyer;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.DepositTxInputsRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import io.bitsquare.trade.states.StateUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.*;

public class ProcessDepositTxInputsRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessDepositTxInputsRequest.class);

    public ProcessDepositTxInputsRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            DepositTxInputsRequest message = (DepositTxInputsRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            processModel.tradingPeer.setPubKeyRing(checkNotNull(message.sellerPubKeyRing));
            processModel.setTakeOfferFeeTxId(nonEmptyStringOf(message.sellerOfferFeeTxId));
            trade.setTradeAmount(positiveCoinOf(nonZeroCoinOf(message.tradeAmount)));
            processModel.tradingPeer.setTradeWalletPubKey(checkNotNull(message.sellerTradeWalletPubKey));

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            StateUtil.setOfferOpenState(trade);
            failed(t);
        }
    }
}