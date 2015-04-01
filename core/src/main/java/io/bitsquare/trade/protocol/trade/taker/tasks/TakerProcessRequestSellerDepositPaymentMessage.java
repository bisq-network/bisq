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
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;
import static io.bitsquare.util.Validator.*;

public class TakerProcessRequestSellerDepositPaymentMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerProcessRequestSellerDepositPaymentMessage.class);

    public TakerProcessRequestSellerDepositPaymentMessage(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPayDepositMessage message = (RequestPayDepositMessage) takerTradeProcessModel.getTradeMessage();
            checkTradeId(takerTradeProcessModel.getId(), message);
            checkNotNull(message);

            takerTradeProcessModel.offerer.setConnectedOutputsForAllInputs(checkNotNull(message.buyerConnectedOutputsForAllInputs));
            checkArgument(message.buyerConnectedOutputsForAllInputs.size() > 0);
            takerTradeProcessModel.offerer.setOutputs(checkNotNull(message.buyerOutputs));
            takerTradeProcessModel.offerer.setTradeWalletPubKey(checkNotNull(message.buyerTradeWalletPubKey));
            takerTradeProcessModel.offerer.setP2pSigPubKey(checkNotNull(message.buyerP2PSigPublicKey));
            takerTradeProcessModel.offerer.setP2pEncryptPubKey(checkNotNull(message.buyerP2PEncryptPublicKey));
            takerTradeProcessModel.offerer.setFiatAccount(checkNotNull(message.buyerFiatAccount));
            takerTradeProcessModel.offerer.setAccountId(nonEmptyStringOf(message.buyerAccountId));

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}