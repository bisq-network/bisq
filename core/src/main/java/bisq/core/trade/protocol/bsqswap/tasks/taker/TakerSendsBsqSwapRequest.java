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

package bisq.core.trade.protocol.bsqswap.tasks.taker;

import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TakerSendsBsqSwapRequest extends BsqSwapTask {

    @SuppressWarnings({"unused"})
    public TakerSendsBsqSwapRequest(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

          /*  checkArgument(!bsqSwapProtocolModel.getOffer().isMyOffer(bsqSwapProtocolModel.getKeyRing()),
                    "must not take own offer");
            checkArgument(bsqSwapProtocolModel.takerPreparesTakerSide(),
                    "Failed to prepare taker side of bsq swap tx");

            var message = new CreateBsqSwapTxRequest(UUID.randomUUID().toString(),
                    bsqSwapProtocolModel.getOffer().getId(),
                    bsqSwapProtocolModel.getMyNodeAddress(),
                    bsqSwapProtocolModel.getPubKeyRing(),
                    bsqSwapProtocolModel.getBsqTradeAmount(),
                    bsqSwapProtocolModel.getBtcTradeAmount(),
                    bsqSwapProtocolModel.getTradePrice(),
                    bsqSwapProtocolModel.getTxFeePerVbyte(),
                    bsqSwapTrade.getMakerFee(),
                    bsqSwapTrade.getTakerFee(),
                    bsqSwapProtocolModel.getTakerBsqOutputAmount(),
                    bsqSwapProtocolModel.getTakerBsqAddress(),
                    bsqSwapProtocolModel.getTakerBtcOutputAmount(),
                    bsqSwapProtocolModel.getTakerBtcAddress(),
                    bsqSwapProtocolModel.getRawTakerBsqInputs(),
                    bsqSwapProtocolModel.getRawTakerBtcInputs());

            log.info("CreateBsqSwapTxRequest={}", message);

            NodeAddress peersNodeAddress = bsqSwapTrade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            bsqSwapProtocolModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    bsqSwapProtocolModel.getTradePeer().getPubKeyRing(),
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
            );*/
        } catch (Throwable t) {
            failed(t);
        }
    }
}
