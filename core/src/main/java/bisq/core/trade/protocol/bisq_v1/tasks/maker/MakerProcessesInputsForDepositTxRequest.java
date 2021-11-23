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

package bisq.core.trade.protocol.bisq_v1.tasks.maker;

import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkTradeId;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MakerProcessesInputsForDepositTxRequest extends TradeTask {
    public MakerProcessesInputsForDepositTxRequest(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            InputsForDepositTxRequest request = (InputsForDepositTxRequest) processModel.getTradeMessage();
            checkNotNull(request);
            checkTradeId(processModel.getOfferId(), request);

            TradingPeer tradingPeer = processModel.getTradePeer();

            // 1.7.0: We do not expect the payment account anymore but in case peer has not updated we still process it.
            Optional.ofNullable(request.getTakerPaymentAccountPayload())
                    .ifPresent(e -> tradingPeer.setPaymentAccountPayload(request.getTakerPaymentAccountPayload()));
            Optional.ofNullable(request.getHashOfTakersPaymentAccountPayload())
                    .ifPresent(e -> tradingPeer.setHashOfPaymentAccountPayload(request.getHashOfTakersPaymentAccountPayload()));
            Optional.ofNullable(request.getTakersPaymentMethodId())
                    .ifPresent(e -> tradingPeer.setPaymentMethodId(request.getTakersPaymentMethodId()));

            tradingPeer.setRawTransactionInputs(checkNotNull(request.getRawTransactionInputs()));
            checkArgument(request.getRawTransactionInputs().size() > 0);

            tradingPeer.setChangeOutputValue(request.getChangeOutputValue());
            tradingPeer.setChangeOutputAddress(request.getChangeOutputAddress());

            tradingPeer.setMultiSigPubKey(checkNotNull(request.getTakerMultiSigPubKey()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(request.getTakerPayoutAddressString()));
            tradingPeer.setPubKeyRing(checkNotNull(request.getTakerPubKeyRing()));

            tradingPeer.setAccountId(nonEmptyStringOf(request.getTakerAccountId()));

            // We set the taker fee only in the processModel yet not in the trade as the tx was only created but not
            // published yet. Once it was published we move it to trade. The takerFeeTx should be sent in a later
            // message but that cannot be changed due backward compatibility issues. It is a left over from the
            // old trade protocol.
            processModel.setTakeOfferFeeTxId(nonEmptyStringOf(request.getTakerFeeTxId()));

            // Taker has to sign offerId (he cannot manipulate that - so we avoid to have a challenge protocol for
            // passing the nonce we want to get signed)
            tradingPeer.setAccountAgeWitnessNonce(trade.getId().getBytes(Charsets.UTF_8));
            tradingPeer.setAccountAgeWitnessSignature(request.getAccountAgeWitnessSignatureOfOfferId());
            tradingPeer.setCurrentDate(request.getCurrentDate());

            User user = checkNotNull(processModel.getUser(), "User must not be null");

            NodeAddress mediatorNodeAddress = checkNotNull(request.getMediatorNodeAddress(),
                    "InputsForDepositTxRequest.getMediatorNodeAddress() must not be null");
            trade.setMediatorNodeAddress(mediatorNodeAddress);
            Mediator mediator = checkNotNull(user.getAcceptedMediatorByAddress(mediatorNodeAddress),
                    "user.getAcceptedMediatorByAddress(mediatorNodeAddress) must not be null");
            trade.setMediatorPubKeyRing(checkNotNull(mediator.getPubKeyRing(),
                    "mediator.getPubKeyRing() must not be null"));

            Offer offer = checkNotNull(trade.getOffer(), "Offer must not be null");
            try {
                long takersTradePrice = request.getTradePrice();
                offer.verifyTakersTradePrice(takersTradePrice);
                trade.setPriceAsLong(takersTradePrice);
            } catch (TradePriceOutOfToleranceException e) {
                failed(e.getMessage());
            } catch (Throwable e2) {
                failed(e2);
            }

            checkArgument(request.getTradeAmount() > 0);
            trade.setAmount(Coin.valueOf(request.getTradeAmount()));

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
