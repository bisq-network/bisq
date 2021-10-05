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

package bisq.core.trade.protocol.bsq_swap.tasks.seller;

import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;
import bisq.core.trade.protocol.messages.bsq_swap.BsqSwapFinalizeTxRequest;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendBsqSwapFinalizeTxRequest extends BsqSwapTask {

    @SuppressWarnings({"unused"})
    public SendBsqSwapFinalizeTxRequest(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BsqSwapFinalizeTxRequest request = new BsqSwapFinalizeTxRequest(
                    protocolModel.getOfferId(),
                    protocolModel.getMyNodeAddress(),
                    Objects.requireNonNull(protocolModel.getTx()),
                    protocolModel.getInputs());

            log.info("FinalizeBsqSwapTxRequest={}", request);

            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    request.getClass().getSimpleName(), peersNodeAddress, request.getTradeId(), request.getUid());
            protocolModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    protocolModel.getTradePeer().getPubKeyRing(),
                    request,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    request.getClass().getSimpleName(), peersNodeAddress, request.getTradeId(),
                                    request.getUid());
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    request.getClass().getSimpleName(), peersNodeAddress, request.getTradeId(),
                                    request.getUid(), errorMessage);

                            appendToErrorMessage("Sending request failed: request=" + request + "\nerrorMessage=" +
                                    errorMessage);
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
