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

import bisq.core.monetary.Price;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.user.User;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.offer.OfferPayload.Direction.BUY;

@Slf4j
class CoreOffersService {

    private final CreateOfferService createOfferService;
    private final OfferBookService offerBookService;
    private final OpenOfferManager openOfferManager;
    private final User user;

    @Inject
    public CoreOffersService(CreateOfferService createOfferService,
                             OfferBookService offerBookService,
                             OpenOfferManager openOfferManager,
                             User user) {
        this.createOfferService = createOfferService;
        this.offerBookService = offerBookService;
        this.openOfferManager = openOfferManager;
        this.user = user;
    }

    public List<Offer> getOffers(String direction, String fiatCurrencyCode) {
        List<Offer> offers = offerBookService.getOffers().stream()
                .filter(o -> {
                    var offerOfWantedDirection = o.getDirection().name().equalsIgnoreCase(direction);
                    var offerInWantedCurrency = o.getOfferPayload().getCounterCurrencyCode().equalsIgnoreCase(fiatCurrencyCode);
                    return offerOfWantedDirection && offerInWantedCurrency;
                })
                .collect(Collectors.toList());

        // A buyer probably wants to see sell orders in price ascending order.
        // A seller probably wants to see buy orders in price descending order.
        if (direction.equalsIgnoreCase(BUY.name()))
            offers.sort(Comparator.comparing(Offer::getPrice).reversed());
        else
            offers.sort(Comparator.comparing(Offer::getPrice));

        return offers;
    }

    public void createOffer(String currencyCode,
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

        //noinspection ConstantConditions
        createOffer(offerId,
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

    public void createOffer(String offerId,
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
        Coin useDefaultTxFee = Coin.ZERO;
        Offer offer = createOfferService.createAndGetOffer(offerId,
                direction,
                currencyCode,
                amount,
                minAmount,
                price,
                useDefaultTxFee,
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
