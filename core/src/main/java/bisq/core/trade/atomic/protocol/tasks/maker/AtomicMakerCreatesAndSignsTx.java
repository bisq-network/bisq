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

package bisq.core.trade.atomic.protocol.tasks.maker;

import bisq.core.dao.governance.param.Param;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxResponse;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicMakerCreatesAndSignsTx extends AtomicTradeTask {
    @SuppressWarnings({"unused"})
    public AtomicMakerCreatesAndSignsTx(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            var makerBsqAddress = atomicProcessModel.getBsqWalletService().getUnusedAddress();
            var makerBtcAddress = atomicProcessModel.getBtcWalletService().getFreshAddressEntry().getAddress();
            checkNotNull(makerBtcAddress, "Maker address must not be null");
            atomicProcessModel.setMakerBsqAddress(makerBsqAddress.toString());
            atomicProcessModel.setMakerBtcAddress(makerBtcAddress.toString());
            var takerBsqAddressInBtcFormat = atomicProcessModel.getBsqWalletService().getBsqFormatter().
                    getAddressFromBsqAddress(atomicProcessModel.getTakerBsqAddress()).toString();

            // Create atomic tx with maker btc inputs signed
            var makerSignedBtcAtomicTx = atomicProcessModel.getTradeWalletService().makerCreatesAndSignsAtomicTx(
                    Coin.valueOf(atomicProcessModel.getMakerBsqOutputAmount()),
                    Coin.valueOf(atomicProcessModel.getMakerBtcOutputAmount()),
                    Coin.valueOf(atomicProcessModel.getTakerBsqOutputAmount()),
                    Coin.valueOf(atomicProcessModel.getTakerBtcOutputAmount()),
                    Coin.valueOf(atomicProcessModel.getBtcTradeFee()),
                    makerBsqAddress.toString(),
                    makerBtcAddress.toString(),
                    takerBsqAddressInBtcFormat,
                    atomicProcessModel.getTakerBtcAddress(),
                    atomicProcessModel.getDaoFacade().getParamValue(Param.RECIPIENT_BTC_ADDRESS),
                    atomicProcessModel.getMakerBsqInputs(),
                    atomicProcessModel.getMakerBtcInputs(),
                    atomicProcessModel.getRawTakerBsqInputs(),
                    atomicProcessModel.getRawTakerBtcInputs());

            // Sign maker bsq inputs
            var makerSignedAtomicTx = atomicProcessModel.getBsqWalletService().signInputs(
                    makerSignedBtcAtomicTx, atomicProcessModel.getMakerBsqInputs());

            atomicProcessModel.setAtomicTx(makerSignedAtomicTx.bitcoinSerialize());
            var message = new CreateAtomicTxResponse(UUID.randomUUID().toString(),
                    atomicProcessModel.getOffer().getId(),
                    atomicProcessModel.getMyNodeAddress(),
                    atomicProcessModel.getAtomicTx());

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
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
