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

package io.bitsquare.trade.protocol.trade.buyer.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxFromTakerMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class TakerProcessRequestPublishDepositTxFromTakerMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerProcessRequestPublishDepositTxFromTakerMessage.class);

    public TakerProcessRequestPublishDepositTxFromTakerMessage(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPublishDepositTxFromTakerMessage message = (RequestPublishDepositTxFromTakerMessage) takerTradeProcessModel.getTradeMessage();
            checkTradeId(takerTradeProcessModel.getId(), message);
            checkNotNull(message);

            takerTradeProcessModel.tradingPeer.setFiatAccount(checkNotNull(message.takerFiatAccount));
            takerTradeProcessModel.tradingPeer.setAccountId(nonEmptyStringOf(message.takerAccountId));
            takerTradeProcessModel.tradingPeer.setP2pSigPubKey(checkNotNull(message.takerP2PSigPublicKey));
            takerTradeProcessModel.tradingPeer.setP2pSigPubKey(checkNotNull(message.takerP2PSigPublicKey));
            takerTradeProcessModel.tradingPeer.setTradeWalletPubKey(checkNotNull(message.sellerTradeWalletPubKey));
            takerTradeProcessModel.tradingPeer.setP2pEncryptPubKey(checkNotNull(message.takerP2PEncryptPublicKey));
            takerTradeProcessModel.tradingPeer.setContractAsJson(nonEmptyStringOf(message.takerContractAsJson));
            takerTradeProcessModel.tradingPeer.setContractSignature(nonEmptyStringOf(message.takerContractSignature));
            takerTradeProcessModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(message.takerPayoutAddressString));
            takerTradeProcessModel.tradingPeer.setPreparedDepositTx(checkNotNull(message.takersPreparedDepositTx));
            takerTradeProcessModel.tradingPeer.setConnectedOutputsForAllInputs(checkNotNull(message.takerConnectedOutputsForAllInputs));
            checkArgument(message.takerConnectedOutputsForAllInputs.size() > 0);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);

            failed(t);
        }
    }
}