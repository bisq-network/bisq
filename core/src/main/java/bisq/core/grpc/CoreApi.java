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

package bisq.core.grpc;

import bisq.core.btc.Balances;
import bisq.core.monetary.Price;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.presentation.BalancePresentation;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.common.app.Version;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Provides high level interface to functionality of core Bisq features.
 * E.g. useful for different APIs to access data of different domains of Bisq.
 */
@Slf4j
public class CoreApi {
    private final Balances balances;
    private final BalancePresentation balancePresentation;
    private final OfferBookService offerBookService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final CreateOfferService createOfferService;
    private final OpenOfferManager openOfferManager;
    private final User user;

    @Inject
    public CoreApi(Balances balances,
                   BalancePresentation balancePresentation,
                   OfferBookService offerBookService,
                   TradeStatisticsManager tradeStatisticsManager,
                   CreateOfferService createOfferService,
                   OpenOfferManager openOfferManager,
                   User user) {
        this.balances = balances;
        this.balancePresentation = balancePresentation;
        this.offerBookService = offerBookService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.createOfferService = createOfferService;
        this.openOfferManager = openOfferManager;
        this.user = user;
    }

    public String getVersion() {
        return Version.VERSION;
    }

    public long getAvailableBalance() {
        return balances.getAvailableBalance().get().getValue();
    }

    public String getAvailableBalanceAsString() {
        return balancePresentation.getAvailableBalance().get();
    }

    public List<TradeStatistics2> getTradeStatistics() {
        return new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
    }

    public List<Offer> getOffers() {
        return offerBookService.getOffers();
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    public void placeOffer(String currencyCode,
                           String directionAsString,
                           long priceAsLong,
                           boolean useMarketBasedPrice,
                           double marketPriceMargin,
                           long amountAsLong,
                           long minAmountAsLong,
                           double buyerSecurityDeposit,
                           String paymentAccountId,
                           TransactionResultHandler resultHandler) {
        String offerId = createOfferService.getRandomOfferId();
        OfferPayload.Direction direction = OfferPayload.Direction.valueOf(directionAsString);
        Price price = Price.valueOf(currencyCode, priceAsLong);
        Coin amount = Coin.valueOf(amountAsLong);
        Coin minAmount = Coin.valueOf(minAmountAsLong);
        PaymentAccount paymentAccount = user.getPaymentAccount(paymentAccountId);
        // We don't support atm funding from external wallet to keep it simple
        boolean useSavingsWallet = true;

        placeOffer(offerId,
                currencyCode,
                direction,
                price,
                useMarketBasedPrice,
                marketPriceMargin,
                amount,
                minAmount,
                buyerSecurityDeposit,
                paymentAccount,
                useSavingsWallet,
                resultHandler);
    }

    public void placeOffer(String offerId,
                           String currencyCode,
                           OfferPayload.Direction direction,
                           Price price,
                           boolean useMarketBasedPrice,
                           double marketPriceMargin,
                           Coin amount,
                           Coin minAmount,
                           double buyerSecurityDeposit,
                           PaymentAccount paymentAccount,
                           boolean useSavingsWallet,
                           TransactionResultHandler resultHandler) {
        Offer offer = createOfferService.createAndGetOffer(offerId,
                direction,
                currencyCode,
                amount,
                minAmount,
                price,
                useMarketBasedPrice,
                marketPriceMargin,
                buyerSecurityDeposit,
                paymentAccount);

        openOfferManager.placeOffer(offer,
                buyerSecurityDeposit,
                useSavingsWallet,
                resultHandler,
                log::error);
    }
}
