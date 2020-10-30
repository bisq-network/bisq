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
import bisq.core.trade.TradeUtil;
import bisq.core.trade.protocol.BuyerProtocol;
import bisq.core.trade.protocol.SellerProtocol;
import bisq.core.user.User;

import javax.inject.Inject;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.format;

@Slf4j
class CoreTradesService {

    private final TakeOfferModel takeOfferModel;
    private final TradeManager tradeManager;
    private final TradeUtil tradeUtil;
    private final User user;

    @Inject
    public CoreTradesService(TakeOfferModel takeOfferModel,
                             TradeManager tradeManager,
                             TradeUtil tradeUtil,
                             User user) {
        this.takeOfferModel = takeOfferModel;
        this.tradeManager = tradeManager;
        this.tradeUtil = tradeUtil;
        this.user = user;
    }

    void takeOffer(Offer offer,
                   String paymentAccountId,
                   Consumer<Trade> resultHandler) {
        var paymentAccount = user.getPaymentAccount(paymentAccountId);
        if (paymentAccount == null)
            throw new IllegalArgumentException(format("payment account with id '%s' not found", paymentAccountId));

        var useSavingsWallet = true;
        //noinspection ConstantConditions
        takeOfferModel.initModel(offer, paymentAccount, useSavingsWallet);
        log.info("Initiating take {} offer, {}",
                offer.isBuyOffer() ? "buy" : "sell",
                takeOfferModel);
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
                resultHandler::accept,
                errorMessage -> {
                    log.error(errorMessage);
                    throw new IllegalStateException(errorMessage);
                }
        );
    }

    void confirmPaymentStarted(String tradeId) {
        var trade = getTrade(tradeId);
        if (isFollowingBuyerProtocol(trade)) {
            var tradeProtocol = tradeManager.getTradeProtocol(trade);
            ((BuyerProtocol) tradeProtocol).onPaymentStarted(
                    () -> {
                    },
                    errorMessage -> {
                        throw new IllegalStateException(errorMessage);
                    }
            );
        } else {
            throw new IllegalStateException("you are the seller and not sending payment");
        }
    }

    void confirmPaymentReceived(String tradeId) {
        var trade = getTrade(tradeId);
        if (isFollowingBuyerProtocol(trade)) {
            throw new IllegalStateException("you are the buyer, and not receiving payment");
        } else {
            var tradeProtocol = tradeManager.getTradeProtocol(trade);
            ((SellerProtocol) tradeProtocol).onPaymentReceived(
                    () -> {
                    },
                    errorMessage -> {
                        throw new IllegalStateException(errorMessage);
                    }
            );
        }
    }

    @SuppressWarnings("unused")
    void keepFunds(String tradeId) {
        log.info("TODO");
    }

    @SuppressWarnings("unused")
    void withdrawFunds(String tradeId, String address) {
        log.info("TODO");
    }

    String getTradeRole(String tradeId) {
        return tradeUtil.getRole(getTrade(tradeId));
    }

    Trade getTrade(String tradeId) {
        return tradeManager.getTradeById(tradeId).orElseThrow(() ->
                new IllegalArgumentException(format("trade with id '%s' not found", tradeId)));
    }

    private boolean isFollowingBuyerProtocol(Trade trade) {
        return tradeManager.getTradeProtocol(trade) instanceof BuyerProtocol;
    }
}
