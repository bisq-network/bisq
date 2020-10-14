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

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.user.User;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import java.math.BigDecimal;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.exactMultiply;
import static bisq.common.util.MathUtils.roundDoubleToLong;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static bisq.core.offer.OfferPayload.Direction;
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

    List<Offer> getOffers(String direction, String currencyCode) {
        List<Offer> offers = offerBookService.getOffers().stream()
                .filter(o -> {
                    var offerOfWantedDirection = o.getDirection().name().equalsIgnoreCase(direction);
                    var offerInWantedCurrency = o.getOfferPayload().getCounterCurrencyCode()
                            .equalsIgnoreCase(currencyCode);
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

    // Create and place new offer.
    void createAndPlaceOffer(String currencyCode,
                             String directionAsString,
                             String priceAsString,
                             boolean useMarketBasedPrice,
                             double marketPriceMargin,
                             long amountAsLong,
                             long minAmountAsLong,
                             double buyerSecurityDeposit,
                             String paymentAccountId,
                             Consumer<Offer> resultHandler) {
        String upperCaseCurrencyCode = currencyCode.toUpperCase();
        String offerId = createOfferService.getRandomOfferId();
        Direction direction = Direction.valueOf(directionAsString.toUpperCase());
        Price price = Price.valueOf(upperCaseCurrencyCode, priceStringToLong(priceAsString, upperCaseCurrencyCode));
        Coin amount = Coin.valueOf(amountAsLong);
        Coin minAmount = Coin.valueOf(minAmountAsLong);
        PaymentAccount paymentAccount = user.getPaymentAccount(paymentAccountId);
        Coin useDefaultTxFee = Coin.ZERO;
        Offer offer = createOfferService.createAndGetOffer(offerId,
                direction,
                upperCaseCurrencyCode,
                amount,
                minAmount,
                price,
                useDefaultTxFee,
                useMarketBasedPrice,
                exactMultiply(marketPriceMargin, 0.01),
                buyerSecurityDeposit,
                paymentAccount);

        // We don't support atm funding from external wallet to keep it simple.
        boolean useSavingsWallet = true;
        //noinspection ConstantConditions
        placeOffer(offer,
                buyerSecurityDeposit,
                useSavingsWallet,
                transaction -> resultHandler.accept(offer));
    }

    // Edit a placed offer.
    Offer editOffer(String offerId,
                    String currencyCode,
                    Direction direction,
                    Price price,
                    boolean useMarketBasedPrice,
                    double marketPriceMargin,
                    Coin amount,
                    Coin minAmount,
                    double buyerSecurityDeposit,
                    PaymentAccount paymentAccount) {
        Coin useDefaultTxFee = Coin.ZERO;
        return createOfferService.createAndGetOffer(offerId,
                direction,
                currencyCode.toUpperCase(),
                amount,
                minAmount,
                price,
                useDefaultTxFee,
                useMarketBasedPrice,
                exactMultiply(marketPriceMargin, 0.01),
                buyerSecurityDeposit,
                paymentAccount);
    }

    private void placeOffer(Offer offer,
                            double buyerSecurityDeposit,
                            boolean useSavingsWallet,
                            Consumer<Transaction> resultHandler) {
        openOfferManager.placeOffer(offer,
                buyerSecurityDeposit,
                useSavingsWallet,
                resultHandler::accept,
                log::error);

        if (offer.getErrorMessage() != null)
            throw new IllegalStateException(offer.getErrorMessage());
    }

    private long priceStringToLong(String priceAsString, String currencyCode) {
        int precision = isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
        double priceAsDouble = new BigDecimal(priceAsString).doubleValue();
        double scaled = scaleUpByPowerOf10(priceAsDouble, precision);
        return roundDoubleToLong(scaled);
    }
}
