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
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class OffererProcessRequestPublishDepositTxMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererProcessRequestPublishDepositTxMessage.class);

    public OffererProcessRequestPublishDepositTxMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPublishDepositTxMessage message = (RequestPublishDepositTxMessage) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), message);
            checkNotNull(message);

            processModel.tradingPeer.setFiatAccount(checkNotNull(message.takerFiatAccount));
            processModel.tradingPeer.setAccountId(nonEmptyStringOf(message.takerAccountId));
            processModel.tradingPeer.setP2pSigPubKey(checkNotNull(message.takerP2PSigPublicKey));
            processModel.tradingPeer.setP2pEncryptPubKey(checkNotNull(message.takerP2PEncryptPublicKey));
            processModel.tradingPeer.setContractAsJson(nonEmptyStringOf(message.takerContractAsJson));
            processModel.tradingPeer.setContractSignature(nonEmptyStringOf(message.takerContractSignature));
            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(message.takerPayoutAddressString));
            processModel.tradingPeer.setPreparedDepositTx(checkNotNull(message.takersPreparedDepositTx));
            processModel.tradingPeer.setConnectedOutputsForAllInputs(checkNotNull(message.takerConnectedOutputsForAllInputs));
            checkArgument(message.takerConnectedOutputsForAllInputs.size() > 0);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);

            if (trade instanceof OffererAsBuyerTrade)
                trade.setLifeCycleState(OffererAsBuyerTrade.LifeCycleState.OFFER_OPEN);
            else if (trade instanceof OffererAsSellerTrade)
                trade.setLifeCycleState(OffererAsSellerTrade.LifeCycleState.OFFER_OPEN);

            failed(t);
        }
    }
}