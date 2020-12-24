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

package bisq.desktop.main;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.AveragePriceUtil;

import bisq.common.util.MathUtils;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;

import javax.annotation.Nullable;

import static bisq.desktop.main.shared.ChatView.log;
import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class PriceUtil {
    private final PriceFeedService priceFeedService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final Preferences preferences;
    @Nullable
    private Price bsq30DayAveragePrice;

    @Inject
    public PriceUtil(PriceFeedService priceFeedService,
                     TradeStatisticsManager tradeStatisticsManager,
                     Preferences preferences) {
        this.priceFeedService = priceFeedService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.preferences = preferences;
    }

    public void recalculateBsq30DayAveragePrice() {
        bsq30DayAveragePrice = null;
        bsq30DayAveragePrice = getBsq30DayAveragePrice();
    }

    public Price getBsq30DayAveragePrice() {
        if (bsq30DayAveragePrice == null) {
            bsq30DayAveragePrice = AveragePriceUtil.getAveragePriceTuple(preferences,
                    tradeStatisticsManager, 30).second;
        }
        return bsq30DayAveragePrice;
    }

    public boolean hasMarketPrice(Offer offer) {
        String currencyCode = offer.getCurrencyCode();
        checkNotNull(priceFeedService, "priceFeed must not be null");
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        Price price = offer.getPrice();
        return price != null && marketPrice != null && marketPrice.isRecentExternalPriceAvailable();
    }

    public Optional<Double> getMarketBasedPrice(Offer offer,
                                                OfferPayload.Direction direction) {
        if (offer.isUseMarketBasedPrice()) {
            return Optional.of(offer.getMarketPriceMargin());
        }

        if (!hasMarketPrice(offer)) {
            if (offer.getCurrencyCode().equals("BSQ")) {
                Price bsq30DayAveragePrice = getBsq30DayAveragePrice();
                if (bsq30DayAveragePrice.isPositive()) {
                    double scaled = MathUtils.scaleDownByPowerOf10(bsq30DayAveragePrice.getValue(), 8);
                    return calculatePercentage(offer, scaled, direction);
                } else {
                    return Optional.empty();
                }
            } else {
                log.trace("We don't have a market price. " +
                        "That case could only happen if you don't have a price feed.");
                return Optional.empty();
            }
        }

        String currencyCode = offer.getCurrencyCode();
        checkNotNull(priceFeedService, "priceFeed must not be null");
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        double marketPriceAsDouble = checkNotNull(marketPrice).getPrice();
        return calculatePercentage(offer, marketPriceAsDouble, direction);
    }

    public Optional<Double> calculatePercentage(Offer offer,
                                                double marketPrice,
                                                OfferPayload.Direction direction) {
        // If the offer did not use % price we calculate % from current market price
        String currencyCode = offer.getCurrencyCode();
        Price price = offer.getPrice();
        int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                Altcoin.SMALLEST_UNIT_EXPONENT :
                Fiat.SMALLEST_UNIT_EXPONENT;
        long priceAsLong = checkNotNull(price).getValue();
        double scaled = MathUtils.scaleDownByPowerOf10(priceAsLong, precision);
        double value;
        if (direction == OfferPayload.Direction.SELL) {
            if (CurrencyUtil.isFiatCurrency(currencyCode)) {
                if (marketPrice == 0) {
                    return Optional.empty();
                }
                value = 1 - scaled / marketPrice;
            } else {
                if (marketPrice == 1) {
                    return Optional.empty();
                }
                value = scaled / marketPrice - 1;
            }
        } else {
            if (CurrencyUtil.isFiatCurrency(currencyCode)) {
                if (marketPrice == 1) {
                    return Optional.empty();
                }
                value = scaled / marketPrice - 1;
            } else {
                if (marketPrice == 0) {
                    return Optional.empty();
                }
                value = 1 - scaled / marketPrice;
            }
        }
        return Optional.of(value);
    }
}
