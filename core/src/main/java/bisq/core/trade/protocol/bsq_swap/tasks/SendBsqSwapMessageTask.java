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

package bisq.core.trade.protocol.bsq_swap.tasks;

import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.TradeMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SendBsqSwapMessageTask extends BsqSwapTask {

    @SuppressWarnings({"unused"})
    public SendBsqSwapMessageTask(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    protected void send(TradeMessage message) {
        NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
        log.info("Send {} to peer {}. tradeId={}, uid={}",
                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
        protocolModel.getP2PService().sendEncryptedDirectMessage(
                peersNodeAddress,
                protocolModel.getTradePeer().getPubKeyRing(),
                message,
                new SendDirectMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(),
                                message.getUid());
                        complete();
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(),
                                message.getUid(), errorMessage);

                        appendToErrorMessage("Sending request failed: request=" + message + "\nerrorMessage=" +
                                errorMessage);
                        failed();
                    }
                }
        );
    }
}
