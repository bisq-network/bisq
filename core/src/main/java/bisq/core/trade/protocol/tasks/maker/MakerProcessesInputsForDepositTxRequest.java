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

import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MakerProcessesInputsForDepositTxRequest extends TradeTask {
    @SuppressWarnings({"unused"})
    public MakerProcessesInputsForDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            InputsForDepositTxRequest inputsForDepositTxRequest = (InputsForDepositTxRequest) processModel.getTradeMessage();
            checkNotNull(inputsForDepositTxRequest);
            checkTradeId(processModel.getOfferId(), inputsForDepositTxRequest);

            final TradingPeer tradingPeer = processModel.getTradingPeer();
            tradingPeer.setPaymentAccountPayload(checkNotNull(inputsForDepositTxRequest.getTakerPaymentAccountPayload()));
            tradingPeer.setRawTransactionInputs(checkNotNull(inputsForDepositTxRequest.getRawTransactionInputs()));
            checkArgument(inputsForDepositTxRequest.getRawTransactionInputs().size() > 0);

            tradingPeer.setChangeOutputValue(inputsForDepositTxRequest.getChangeOutputValue());
            tradingPeer.setChangeOutputAddress(inputsForDepositTxRequest.getChangeOutputAddress());

            tradingPeer.setMultiSigPubKey(checkNotNull(inputsForDepositTxRequest.getTakerMultiSigPubKey()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(inputsForDepositTxRequest.getTakerPayoutAddressString()));
            tradingPeer.setPubKeyRing(checkNotNull(inputsForDepositTxRequest.getTakerPubKeyRing()));

            tradingPeer.setAccountId(nonEmptyStringOf(inputsForDepositTxRequest.getTakerAccountId()));
            trade.setTakerFeeTxId(nonEmptyStringOf(inputsForDepositTxRequest.getTakerFeeTxId()));

            // Taker has to sign offerId (he cannot manipulate that - so we avoid to have a challenge protocol for passing the nonce we want to get signed)
            tradingPeer.setAccountAgeWitnessNonce(trade.getId().getBytes(Charsets.UTF_8));
            tradingPeer.setAccountAgeWitnessSignature(inputsForDepositTxRequest.getAccountAgeWitnessSignatureOfOfferId());
            tradingPeer.setCurrentDate(inputsForDepositTxRequest.getCurrentDate());

            User user = checkNotNull(processModel.getUser(), "User must not be null");

            NodeAddress mediatorNodeAddress = checkNotNull(inputsForDepositTxRequest.getMediatorNodeAddress(),
                    "payDepositRequest.getMediatorNodeAddress() must not be null");
            trade.setMediatorNodeAddress(mediatorNodeAddress);
            Mediator mediator = checkNotNull(user.getAcceptedMediatorByAddress(mediatorNodeAddress),
                    "user.getAcceptedMediatorByAddress(mediatorNodeAddress) must not be null");
            trade.setMediatorPubKeyRing(checkNotNull(mediator.getPubKeyRing(),
                    "mediator.getPubKeyRing() must not be null"));

            Offer offer = checkNotNull(trade.getOffer(), "Offer must not be null");
            try {
                long takersTradePrice = inputsForDepositTxRequest.getTradePrice();
                offer.checkTradePriceTolerance(takersTradePrice);
                trade.setTradePrice(takersTradePrice);
            } catch (TradePriceOutOfToleranceException e) {
                failed(e.getMessage());
            } catch (Throwable e2) {
                failed(e2);
            }

            checkArgument(inputsForDepositTxRequest.getTradeAmount() > 0);
            trade.setTradeAmount(Coin.valueOf(inputsForDepositTxRequest.getTradeAmount()));

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            trade.persist();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
