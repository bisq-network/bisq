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

package bisq.core.trade.protocol.bisq_v5.tasks.buyer;

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.messages.PreparedTxBuyerSignaturesMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSendsPreparedTxBuyerSignaturesMessage extends TradeTask {
    public BuyerSendsPreparedTxBuyerSignaturesMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            byte[] depositTxWithBuyerWitnesses = processModel.getDepositTx() != null
                    ? processModel.getDepositTx().bitcoinSerialize() // set in BuyerAsTakerSignsDepositTx task
                    : processModel.getPreparedDepositTx();           // set in BuyerAsMakerCreatesAndSignsDepositTx task
            byte[] buyersWarningTxBuyerSignature = processModel.getWarningTxBuyerSignature();
            byte[] sellersWarningTxBuyerSignature = processModel.getTradePeer().getWarningTxBuyerSignature();
            byte[] buyersRedirectTxBuyerSignature = processModel.getRedirectTxBuyerSignature();
            byte[] sellersRedirectTxBuyerSignature = processModel.getTradePeer().getRedirectTxBuyerSignature();

            PreparedTxBuyerSignaturesMessage message = new PreparedTxBuyerSignaturesMessage(
                    processModel.getOfferId(),
                    UUID.randomUUID().toString(),
                    processModel.getMyNodeAddress(),
                    depositTxWithBuyerWitnesses,
                    buyersWarningTxBuyerSignature,
                    sellersWarningTxBuyerSignature,
                    buyersRedirectTxBuyerSignature,
                    sellersRedirectTxBuyerSignature);

            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            processModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    processModel.getTradePeer().getPubKeyRing(),
                    message,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                            appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" + errorMessage);
                            failed(errorMessage);
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
