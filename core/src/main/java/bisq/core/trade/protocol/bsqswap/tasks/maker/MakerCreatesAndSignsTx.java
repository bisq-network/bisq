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

package bisq.core.trade.protocol.bsqswap.tasks.maker;

import bisq.core.trade.messages.bsqswap.CreateBsqSwapTxResponse;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MakerCreatesAndSignsTx extends BsqSwapTask {
    @SuppressWarnings({"unused"})
    public MakerCreatesAndSignsTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // Create bsq swap tx with maker btc inputs signed
            var swapTx = bsqSwapProtocolModel.createBsqSwapTx();

            // Sign inputs
            swapTx = bsqSwapProtocolModel.getTradeWalletService().signInputs(swapTx,
                    bsqSwapProtocolModel.getRawMakerBtcInputs());
            swapTx = bsqSwapProtocolModel.getBsqWalletService().signInputs(swapTx,
                    bsqSwapProtocolModel.getRawMakerBsqInputs());

            bsqSwapProtocolModel.setRawTx(swapTx.bitcoinSerialize());
            var message = new CreateBsqSwapTxResponse(UUID.randomUUID().toString(),
                    bsqSwapProtocolModel.getOffer().getId(),
                    bsqSwapProtocolModel.getMyNodeAddress(),
                    bsqSwapProtocolModel.getRawTx(),
                    bsqSwapProtocolModel.getMakerBsqOutputAmount(),
                    bsqSwapProtocolModel.getMakerBsqAddress(),
                    bsqSwapProtocolModel.getMakerBtcOutputAmount(),
                    bsqSwapProtocolModel.getMakerBtcAddress(),
                    bsqSwapProtocolModel.getRawMakerBsqInputs(),
                    bsqSwapProtocolModel.getRawMakerBtcInputs());

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
            );
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
