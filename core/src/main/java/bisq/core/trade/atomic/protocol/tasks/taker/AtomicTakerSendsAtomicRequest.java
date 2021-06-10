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

package bisq.core.trade.atomic.protocol.tasks.taker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicTakerSendsAtomicRequest extends AtomicTradeTask {

    @SuppressWarnings({"unused"})
    public AtomicTakerSendsAtomicRequest(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkArgument(!atomicProcessModel.getOffer().isMyOffer(atomicProcessModel.getKeyRing()),
                    "must not take own offer");
            checkArgument(atomicProcessModel.takerPreparesTakerSide(),
                    "Failed to prepare taker side of atomic tx");

            var message = new CreateAtomicTxRequest(UUID.randomUUID().toString(),
                    atomicProcessModel.getOffer().getId(),
                    atomicProcessModel.getMyNodeAddress(),
                    atomicProcessModel.getPubKeyRing(),
                    atomicProcessModel.getBsqTradeAmount(),
                    atomicProcessModel.getBtcTradeAmount(),
                    atomicProcessModel.getTradePrice(),
                    atomicProcessModel.getTxFeePerVbyte(),
                    atomicTrade.getMakerFee(),
                    atomicTrade.getTakerFee(),
                    atomicProcessModel.getTakerBsqOutputAmount(),
                    atomicProcessModel.getTakerBsqAddress(),
                    atomicProcessModel.getTakerBtcOutputAmount(),
                    atomicProcessModel.getTakerBtcAddress(),
                    atomicProcessModel.getRawTakerBsqInputs(),
                    atomicProcessModel.getRawTakerBtcInputs());

            log.info("atomictxrequest={}", message.toString());

            NodeAddress peersNodeAddress = atomicTrade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            atomicProcessModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    atomicProcessModel.getTradingPeer().getPubKeyRing(),
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

                            appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" +
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
