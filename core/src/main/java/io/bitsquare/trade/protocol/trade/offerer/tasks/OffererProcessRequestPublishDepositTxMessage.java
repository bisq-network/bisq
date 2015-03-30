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
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class OffererProcessRequestPublishDepositTxMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererProcessRequestPublishDepositTxMessage.class);

    public OffererProcessRequestPublishDepositTxMessage(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            RequestPublishDepositTxMessage message = (RequestPublishDepositTxMessage) offererTradeProcessModel.getTradeMessage();
            checkTradeId(offererTradeProcessModel.getId(), message);
            checkNotNull(message);

            offererTradeProcessModel.taker.setFiatAccount(checkNotNull(message.takerFiatAccount));
            offererTradeProcessModel.taker.setAccountId(nonEmptyStringOf(message.takerAccountId));
            offererTradeProcessModel.taker.setP2pSigPubKey(checkNotNull(message.takerP2PSigPublicKey));
            offererTradeProcessModel.taker.setP2pEncryptPubKey(checkNotNull(message.takerP2PEncryptPublicKey));
            offererTradeProcessModel.taker.setContractAsJson(nonEmptyStringOf(message.takerContractAsJson));
            offererTradeProcessModel.taker.setContractSignature(nonEmptyStringOf(message.takerContractSignature));
            offererTradeProcessModel.taker.setPayoutAddressString(nonEmptyStringOf(message.takerPayoutAddressString));
            offererTradeProcessModel.taker.setPreparedDepositTx(checkNotNull(message.takersPreparedDepositTx));
            offererTradeProcessModel.taker.setConnectedOutputsForAllInputs(checkNotNull(message.takerConnectedOutputsForAllInputs));
            checkArgument(message.takerConnectedOutputsForAllInputs.size() > 0);

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