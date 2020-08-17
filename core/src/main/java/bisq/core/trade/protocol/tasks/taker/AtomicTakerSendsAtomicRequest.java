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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CreateAtomicTxRequest;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicTakerSendsAtomicRequest extends TradeTask {

    @SuppressWarnings({"unused"})
    public AtomicTakerSendsAtomicRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkArgument(!processModel.getOffer().isMyOffer(processModel.getKeyRing()), "must not take own offer");
            var isBuyer = !processModel.getOffer().isBuyOffer();

            var atomicModel = processModel.getAtomicModel();
            atomicModel.initFromTrade(trade);
            atomicModel.setTakerBsqAddress(processModel.getBsqWalletService().getUnusedBsqAddressAsString());
            atomicModel.setTakerBtcAddress(processModel.getBtcWalletService().
                    getNewAddressEntry(trade.getId(), AddressEntry.Context.TRADE_PAYOUT).getAddressString());

            // Prepare BSQ inputs
            var requiredBsq = atomicModel.getBsqTradeFee() + (isBuyer ? atomicModel.getBsqTradeAmount() : 0L);
            var preparedBsq = processModel.getBsqWalletService().prepareAtomicBsqInputs(Coin.valueOf(requiredBsq));
            var takerBsqOutputAmount = preparedBsq.second.getValue() + (isBuyer ? 0L : atomicModel.getBsqTradeAmount());
            atomicModel.setTakerBsqOutputAmount(takerBsqOutputAmount);

            // Prepare BTC inputs
            var preparedAtomicTxData = processModel.getTradeWalletService().takerPreparesAtomicTx(
                    preparedBsq.first,
                    Coin.valueOf(isBuyer ? 0L : atomicModel.getBtcTradeAmount()),
                    Coin.valueOf(atomicModel.getTxFee()),
                    Coin.valueOf(atomicModel.getBtcTradeFee()),
                    Coin.valueOf(atomicModel.getBsqTradeFee()));
            atomicModel.setRawTakerBsqInputs(preparedAtomicTxData.first);
            atomicModel.setRawTakerBtcInputs(preparedAtomicTxData.second);
            atomicModel.setTxFee(preparedAtomicTxData.third.getValue());
            var takerBtcOutputAmount = preparedAtomicTxData.fourth.getValue() +
                    (isBuyer ? atomicModel.getBtcTradeAmount() : 0L);
            atomicModel.setTakerBtcOutputAmount(takerBtcOutputAmount);

            var message = new CreateAtomicTxRequest(UUID.randomUUID().toString(),
                    processModel.getOfferId(),
                    processModel.getMyNodeAddress(),
                    processModel.getPubKeyRing(),
                    atomicModel.getBsqTradeAmount(),
                    atomicModel.getBtcTradeAmount(),
                    atomicModel.getTradePrice(),
                    atomicModel.getTxFee(),
                    trade.getTakerFeeAsLong(),
                    trade.isCurrencyForTakerFeeBtc(),
                    atomicModel.getTakerBsqOutputAmount(),
                    atomicModel.getTakerBsqAddress(),
                    atomicModel.getTakerBtcOutputAmount(),
                    atomicModel.getTakerBtcAddress(),
                    atomicModel.getRawTakerBsqInputs(),
                    atomicModel.getRawTakerBtcInputs());

            log.info("atomictxrequest={}", message.toString());

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
        } catch (Throwable t) {
            failed(t);
        }
    }
}
