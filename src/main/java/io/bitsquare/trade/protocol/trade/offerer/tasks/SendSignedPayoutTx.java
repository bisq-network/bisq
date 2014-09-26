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

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import io.bitsquare.trade.protocol.trade.offerer.messages.BankTransferInitedMessage;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.ECKey;

import javafx.util.Pair;

import net.tomp2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedPayoutTx {
    private static final Logger log = LoggerFactory.getLogger(SendSignedPayoutTx.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           WalletFacade walletFacade,
                           String tradeId,
                           String takerPayoutAddress,
                           String offererPayoutAddress,
                           String depositTransactionId,
                           Coin collateral,
                           Coin tradeAmount) {
        log.trace("Run task");
        try {
            Coin offererPaybackAmount = tradeAmount.add(collateral);
            @SuppressWarnings("UnnecessaryLocalVariable") Coin takerPaybackAmount = collateral;

            Pair<ECKey.ECDSASignature, String> result = walletFacade.offererCreatesAndSignsPayoutTx(
                    depositTransactionId, offererPaybackAmount, takerPaybackAmount, takerPayoutAddress, tradeId);

            ECKey.ECDSASignature offererSignature = result.getKey();
            String offererSignatureR = offererSignature.r.toString();
            String offererSignatureS = offererSignature.s.toString();
            String depositTxAsHex = result.getValue();

            BankTransferInitedMessage tradeMessage = new BankTransferInitedMessage(tradeId,
                    depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererPayoutAddress);

            messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener() {
                @Override
                public void onResult() {
                    log.trace("BankTransferInitedMessage successfully arrived at peer");
                    resultHandler.onResult();
                }

                @Override
                public void onFailed() {
                    log.error("BankTransferInitedMessage did not arrive at peer");
                    exceptionHandler.onError(new Exception("BankTransferInitedMessage did not arrive at peer"));

                }
            });
        } catch (Exception e) {
            log.error("Exception at OffererCreatesAndSignsPayoutTx " + e);
            exceptionHandler.onError(e);
        }
    }
}
