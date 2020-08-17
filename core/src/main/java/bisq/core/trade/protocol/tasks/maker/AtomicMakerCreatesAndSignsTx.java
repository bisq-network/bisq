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

package bisq.core.trade.protocol.tasks.maker;

import bisq.core.dao.governance.param.Param;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CreateAtomicTxResponse;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AtomicMakerCreatesAndSignsTx extends TradeTask {
    @SuppressWarnings({"unused"})
    public AtomicMakerCreatesAndSignsTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            var atomicModel = processModel.getAtomicModel();
            var makerBsqAddress = processModel.getBsqWalletService().getUnusedAddress();
            var makerBtcAddress = processModel.getBtcWalletService().getFreshAddressEntry().getAddress();
            checkNotNull(makerBtcAddress, "Maker address must not be null");
            atomicModel.setMakerBsqAddress(makerBsqAddress.toString());
            atomicModel.setMakerBtcAddress(makerBtcAddress.toString());
            var takerBsqAddressInBtcFormat = processModel.getBsqWalletService().getBsqFormatter().
                    getAddressFromBsqAddress(atomicModel.getTakerBsqAddress()).toString();

            // Create atomic tx with maker btc inputs signed
            var makerSignedBtcAtomicTx = processModel.getTradeWalletService().makerCreatesAndSignsAtomicTx(
                    Coin.valueOf(atomicModel.getMakerBsqOutputAmount()),
                    Coin.valueOf(atomicModel.getMakerBtcOutputAmount()),
                    Coin.valueOf(atomicModel.getTakerBsqOutputAmount()),
                    Coin.valueOf(atomicModel.getTakerBtcOutputAmount()),
                    Coin.valueOf(atomicModel.getBtcTradeFee()),
                    makerBsqAddress.toString(),
                    makerBtcAddress.toString(),
                    takerBsqAddressInBtcFormat,
                    atomicModel.getTakerBtcAddress(),
                    processModel.getDaoFacade().getParamValue(Param.RECIPIENT_BTC_ADDRESS),
                    atomicModel.getMakerBsqInputs(),
                    atomicModel.getMakerBtcInputs(),
                    atomicModel.getRawTakerBsqInputs(),
                    atomicModel.getRawTakerBtcInputs());

            // Sign maker bsq inputs
            var makerSignedAtomicTx = processModel.getBsqWalletService().signInputs(
                    makerSignedBtcAtomicTx, atomicModel.getMakerBsqInputs());

            atomicModel.setAtomicTx(makerSignedAtomicTx.bitcoinSerialize());
            var message = new CreateAtomicTxResponse(UUID.randomUUID().toString(),
                    processModel.getOfferId(),
                    processModel.getMyNodeAddress(),
                    atomicModel.getAtomicTx());

            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            processModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    processModel.getTradingPeer().getPubKeyRing(),
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
