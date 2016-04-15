/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.offerer;

import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.payment.CryptoCurrencyAccountContractData;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.bitsquare.util.Validator.checkTradeId;
import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class ProcessPayDepositRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(ProcessPayDepositRequest.class);

    public ProcessPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            log.debug("current trade state " + trade.getState());
            PayDepositRequest payDepositRequest = (PayDepositRequest) processModel.getTradeMessage();
            checkTradeId(processModel.getId(), payDepositRequest);
            checkNotNull(payDepositRequest);

            processModel.tradingPeer.setRawTransactionInputs(checkNotNull(payDepositRequest.rawTransactionInputs));
            checkArgument(payDepositRequest.rawTransactionInputs.size() > 0);

            processModel.tradingPeer.setChangeOutputValue(payDepositRequest.changeOutputValue);
            if (payDepositRequest.changeOutputAddress != null)
                processModel.tradingPeer.setChangeOutputAddress(payDepositRequest.changeOutputAddress);

            processModel.tradingPeer.setMultiSigPubKey(checkNotNull(payDepositRequest.takerMultiSigPubKey));
            processModel.tradingPeer.setPayoutAddressString(nonEmptyStringOf(payDepositRequest.takerPayoutAddressString));
            processModel.tradingPeer.setPubKeyRing(checkNotNull(payDepositRequest.takerPubKeyRing));

            PaymentAccountContractData paymentAccountContractData = checkNotNull(payDepositRequest.takerPaymentAccountContractData);
            processModel.tradingPeer.setPaymentAccountContractData(paymentAccountContractData);
            // We apply the payment ID in case its a cryptoNote coin. It is created form the hash of the trade ID
            if (paymentAccountContractData instanceof CryptoCurrencyAccountContractData &&
                    CurrencyUtil.isCryptoNoteCoin(processModel.getOffer().getCurrencyCode())) {
                String paymentId = Hash.getHashAsHex(trade.getId()).substring(0, Math.min(32, trade.getId().length()));
                ((CryptoCurrencyAccountContractData) paymentAccountContractData).setPaymentId(paymentId);
            }

            processModel.tradingPeer.setAccountId(nonEmptyStringOf(payDepositRequest.takerAccountId));
            trade.setTakeOfferFeeTxId(nonEmptyStringOf(payDepositRequest.takeOfferFeeTxId));
            processModel.setTakerAcceptedArbitratorNodeAddresses(checkNotNull(payDepositRequest.acceptedArbitratorNodeAddresses));
            if (payDepositRequest.acceptedArbitratorNodeAddresses.size() < 1)
                failed("acceptedArbitratorNames size must be at least 1");
            trade.setArbitratorNodeAddress(checkNotNull(payDepositRequest.arbitratorNodeAddress));

            long takersTradePrice = payDepositRequest.tradePrice;
            checkArgument(takersTradePrice > 0);
            Fiat tradePriceAsFiat = Fiat.valueOf(trade.getOffer().getCurrencyCode(), takersTradePrice);
            Fiat offerPriceAsFiat = trade.getOffer().getPrice();
            double factor = (double) takersTradePrice / (double) offerPriceAsFiat.value;
            // We allow max. 2 % difference between own offer price calculation and takers calculation.
            // Market price might be different at offerers and takers side so we need a bit of tolerance.
            // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations 
            // from one provider.
            if (Math.abs(1 - factor) > 0.02) {
                String msg = "Takers tradePrice is outside our market price tolerance.\n" +
                        "tradePriceAsFiat=" + tradePriceAsFiat.toFriendlyString() + "\n" +
                        "offerPriceAsFiat=" + offerPriceAsFiat.toFriendlyString();
                log.warn(msg);
                failed(msg);
            }
            trade.setTradePrice(takersTradePrice);
            
            
            checkArgument(payDepositRequest.tradeAmount > 0);
            trade.setTradeAmount(Coin.valueOf(payDepositRequest.tradeAmount));

            // update to the latest peer address of our peer if the payDepositRequest is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            removeMailboxMessageAfterProcessing();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}