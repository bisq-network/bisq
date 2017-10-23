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

package io.bisq.core.trade.protocol.tasks.maker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.exceptions.TradePriceOutOfToleranceException;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.util.Validator.checkTradeId;
import static io.bisq.core.util.Validator.nonEmptyStringOf;

@Slf4j
public class MakerProcessPayDepositRequest extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public MakerProcessPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PayDepositRequest payDepositRequest = (PayDepositRequest) processModel.getTradeMessage();
            checkNotNull(payDepositRequest);
            checkTradeId(processModel.getOfferId(), payDepositRequest);

            final TradingPeer tradingPeer = processModel.getTradingPeer();
            tradingPeer.setPaymentAccountPayload(checkNotNull(payDepositRequest.getTakerPaymentAccountPayload()));
            tradingPeer.setRawTransactionInputs(checkNotNull(payDepositRequest.getRawTransactionInputs()));
            checkArgument(payDepositRequest.getRawTransactionInputs().size() > 0);

            tradingPeer.setChangeOutputValue(payDepositRequest.getChangeOutputValue());
            tradingPeer.setChangeOutputAddress(payDepositRequest.getChangeOutputAddress());

            tradingPeer.setMultiSigPubKey(checkNotNull(payDepositRequest.getTakerMultiSigPubKey()));
            tradingPeer.setPayoutAddressString(nonEmptyStringOf(payDepositRequest.getTakerPayoutAddressString()));
            tradingPeer.setPubKeyRing(checkNotNull(payDepositRequest.getTakerPubKeyRing()));

            tradingPeer.setAccountId(nonEmptyStringOf(payDepositRequest.getTakerAccountId()));
            trade.setTakerFeeTxId(nonEmptyStringOf(payDepositRequest.getTakerFeeTxId()));
            processModel.setTakerAcceptedArbitratorNodeAddresses(checkNotNull(payDepositRequest.getAcceptedArbitratorNodeAddresses()));
            processModel.setTakerAcceptedMediatorNodeAddresses(checkNotNull(payDepositRequest.getAcceptedMediatorNodeAddresses()));
            if (payDepositRequest.getAcceptedArbitratorNodeAddresses().isEmpty())
                failed("acceptedArbitratorNodeAddresses must not be empty");

            // Taker has to sign offerId (he cannot manipulate that - so we avoid to have a challenge protocol for passing the nonce we want to get signed)
            tradingPeer.setAccountAgeWitnessNonce(trade.getOffer().getId().getBytes());
            tradingPeer.setAccountAgeWitnessSignature(payDepositRequest.getAccountAgeWitnessSignatureOfOfferId());
            tradingPeer.setCurrentDate(payDepositRequest.getCurrentDate());

            trade.setArbitratorNodeAddress(checkNotNull(payDepositRequest.getArbitratorNodeAddress()));
            trade.setMediatorNodeAddress(checkNotNull(payDepositRequest.getMediatorNodeAddress()));

            try {
                long takersTradePrice = payDepositRequest.getTradePrice();
                trade.getOffer().checkTradePriceTolerance(takersTradePrice);
                trade.setTradePrice(takersTradePrice);
            } catch (TradePriceOutOfToleranceException e) {
                failed(e.getMessage());
            } catch (Throwable e2) {
                failed(e2);
            }

            checkArgument(payDepositRequest.getTradeAmount() > 0);
            trade.setTradeAmount(Coin.valueOf(payDepositRequest.getTradeAmount()));

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.removeMailboxMessageAfterProcessing(trade);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
