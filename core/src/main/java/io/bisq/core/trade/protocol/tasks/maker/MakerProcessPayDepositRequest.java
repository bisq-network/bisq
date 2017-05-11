/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.maker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.exceptions.TradePriceOutOfToleranceException;
import io.bisq.core.filter.PaymentAccountFilter;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.PayDepositRequest;
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

            PaymentAccountPayload paymentAccountPayload = checkNotNull(payDepositRequest.takerPaymentAccountPayload);
            final PaymentAccountFilter[] appliedPaymentAccountFilter = new PaymentAccountFilter[1];
            if (processModel.isPeersPaymentAccountDataAreBanned(paymentAccountPayload, appliedPaymentAccountFilter)) {
                failed("Other trader is banned by his trading account data.\n" +
                        "paymentAccountPayload=" + paymentAccountPayload.getPaymentDetails() + "\n" +
                        "banFilter=" + appliedPaymentAccountFilter[0].toString());
                return;
            }
            processModel.getTradingPeer().setPaymentAccountPayload(paymentAccountPayload);

            processModel.getTradingPeer().setRawTransactionInputs(checkNotNull(payDepositRequest.rawTransactionInputs));
            checkArgument(payDepositRequest.rawTransactionInputs.size() > 0);

            processModel.getTradingPeer().setChangeOutputValue(payDepositRequest.changeOutputValue);
            processModel.getTradingPeer().setChangeOutputAddress(payDepositRequest.changeOutputAddress);

            processModel.getTradingPeer().setMultiSigPubKey(checkNotNull(payDepositRequest.takerMultiSigPubKey));
            processModel.getTradingPeer().setPayoutAddressString(nonEmptyStringOf(payDepositRequest.takerPayoutAddressString));
            processModel.getTradingPeer().setPubKeyRing(checkNotNull(payDepositRequest.takerPubKeyRing));

            processModel.getTradingPeer().setAccountId(nonEmptyStringOf(payDepositRequest.takerAccountId));
            trade.setTakerFeeTxId(nonEmptyStringOf(payDepositRequest.takeOfferFeeTxId));
            processModel.setTakerAcceptedArbitratorNodeAddresses(checkNotNull(payDepositRequest.acceptedArbitratorNodeAddresses));
            processModel.setTakerAcceptedMediatorNodeAddresses(checkNotNull(payDepositRequest.acceptedMediatorNodeAddresses));
            if (payDepositRequest.acceptedArbitratorNodeAddresses.isEmpty())
                failed("acceptedArbitratorNames must not be empty");
            trade.applyArbitratorNodeAddress(checkNotNull(payDepositRequest.arbitratorNodeAddress));
            trade.applyMediatorNodeAddress(checkNotNull(payDepositRequest.mediatorNodeAddress));

            try {
                long takersTradePrice = payDepositRequest.tradePrice;
                trade.getOffer().checkTradePriceTolerance(takersTradePrice);
                trade.setTradePrice(takersTradePrice);
            } catch (TradePriceOutOfToleranceException e) {
                failed(e.getMessage());
            } catch (Throwable e2) {
                failed(e2);
            }

            checkArgument(payDepositRequest.tradeAmount > 0);
            trade.setTradeAmount(Coin.valueOf(payDepositRequest.tradeAmount));

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.removeMailboxMessageAfterProcessing(trade);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}