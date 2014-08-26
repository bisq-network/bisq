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

package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Coin;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTakeOfferFeePayedTxId {
    private static final Logger log = LoggerFactory.getLogger(SendTakeOfferFeePayedTxId.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           String tradeId,
                           String takeOfferFeeTxId,
                           Coin tradeAmount,
                           String pubKeyForThatTradeAsHex) {
        log.trace("Run task");
        TakeOfferFeePayedMessage msg = new TakeOfferFeePayedMessage(tradeId, takeOfferFeeTxId, tradeAmount, pubKeyForThatTradeAsHex);

        messageFacade.sendTradeMessage(peerAddress, msg, new OutgoingTradeMessageListener() {
            @Override
            public void onResult() {
                log.trace("TakeOfferFeePayedMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed() {
                log.error("TakeOfferFeePayedMessage  did not arrive at peer");
                exceptionHandler.onError(new Exception("TakeOfferFeePayedMessage did not arrive at peer"));
            }
        });
    }
}
