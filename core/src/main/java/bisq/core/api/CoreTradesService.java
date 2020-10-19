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

package bisq.core.api;

import bisq.core.offer.Offer;
import bisq.core.offer.takeoffer.TakeOfferModel;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.protocol.BuyerProtocol;
import bisq.core.user.User;

import javax.inject.Inject;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

@Slf4j
class CoreTradesService {

    private final TakeOfferModel takeOfferModel;
    private final TradeManager tradeManager;
    private final User user;

    @Inject
    public CoreTradesService(TakeOfferModel takeOfferModel,
                             TradeManager tradeManager,
                             User user) {
        this.takeOfferModel = takeOfferModel;
        this.tradeManager = tradeManager;
        this.user = user;
    }

    void takeOffer(Offer offer,
                   String paymentAccountId,
                   Consumer<Trade> resultHandler) {
        log.info("Get offer with id {}", offer.getId());
        var paymentAccount = user.getPaymentAccount(paymentAccountId);
        if (paymentAccount == null)
            throw new IllegalArgumentException(format("payment account with id '%s' not found", paymentAccountId));

        var useSavingsWallet = true;
        takeOfferModel.initModel(offer, paymentAccount, useSavingsWallet);
        log.info(takeOfferModel.toString());
        //noinspection ConstantConditions
        tradeManager.onTakeOffer(offer.getAmount(),
                takeOfferModel.getTxFeeFromFeeService(),
                takeOfferModel.getTakerFee(),
                takeOfferModel.isCurrencyForTakerFeeBtc(),
                offer.getPrice().getValue(),
                takeOfferModel.getFundsNeededForTrade(),
                offer,
                paymentAccountId,
                useSavingsWallet,
                trade -> {
                    resultHandler.accept(trade);
                },
                errorMessage -> {
                    log.error(errorMessage);
                    throw new IllegalStateException(errorMessage);
                }
        );
    }

    void confirmPaymentStarted(String tradeId) {
        var trade = getTradeWithId(tradeId);
        var tradeProtocol = tradeManager.getTradeProtocol(trade);
        if (trade.getOffer().isBuyOffer()) {
            ((BuyerProtocol) tradeProtocol).onPaymentStarted(
                    () -> {
                    },
                    errorMessage -> {
                        throw new IllegalStateException(errorMessage);
                    }
            );
        } else {
            throw new IllegalStateException("you are not the buyer and should not try to send payment");
        }
    }

    Trade getTrade(String tradeId) {
        return getTradeWithId(tradeId);
    }

    private Trade getTradeWithId(String tradeId) {
        return tradeManager.getTradeById(tradeId).orElseThrow(() ->
                new IllegalArgumentException(format("trade with id '%s' not found", tradeId)));
    }
}
