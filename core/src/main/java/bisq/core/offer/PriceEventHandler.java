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

package bisq.core.offer;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;

import bisq.common.util.MathUtils;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.ListChangeListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.roundDoubleToLong;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;

@Slf4j
@Singleton
public class PriceEventHandler {
    private final OpenOfferManager openOfferManager;
    private final PriceFeedService priceFeedService;
    private final Map<String, Set<OpenOffer>> openOffersByCurrency = new HashMap<>();

    @Inject
    public PriceEventHandler(OpenOfferManager openOfferManager, PriceFeedService priceFeedService) {
        this.openOfferManager = openOfferManager;
        this.priceFeedService = priceFeedService;
    }

    public void onAllServicesInitialized() {
        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> {
            c.next();
            if (c.wasAdded()) {
                onAddedOpenOffers(c.getAddedSubList());
            }
            if (c.wasRemoved()) {
                onRemovedOpenOffers(c.getRemoved());
            }
        });
        onAddedOpenOffers(openOfferManager.getObservableList());

        priceFeedService.updateCounterProperty().addListener((observable, oldValue, newValue) -> onPriceFeedChanged());
        onPriceFeedChanged();
    }

    private void onPriceFeedChanged() {
        openOffersByCurrency.keySet().stream()
                .map(priceFeedService::getMarketPrice)
                .filter(Objects::nonNull)
                .filter(marketPrice -> openOffersByCurrency.containsKey(marketPrice.getCurrencyCode()))
                .forEach(marketPrice -> {
                    openOffersByCurrency.get(marketPrice.getCurrencyCode()).stream()
                            .filter(openOffer -> !openOffer.isDeactivated())
                            .forEach(openOffer -> checkPriceThreshold(marketPrice, openOffer));
                });
    }

    private void checkPriceThreshold(bisq.core.provider.price.MarketPrice marketPrice, OpenOffer openOffer) {
        Price price = openOffer.getOffer().getPrice();
        if (price == null) {
            return;
        }

        String currencyCode = openOffer.getOffer().getCurrencyCode();
        int smallestUnitExponent = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                Altcoin.SMALLEST_UNIT_EXPONENT :
                Fiat.SMALLEST_UNIT_EXPONENT;
        long marketPriceAsLong = roundDoubleToLong(
                scaleUpByPowerOf10(marketPrice.getPrice(), smallestUnitExponent));
        long triggerPrice = openOffer.getTriggerPrice();
        if (triggerPrice > 0) {
            OfferPayload.Direction direction = openOffer.getOffer().getDirection();
            boolean triggered = direction == OfferPayload.Direction.BUY ?
                    marketPriceAsLong > triggerPrice :
                    marketPriceAsLong < triggerPrice;
            if (triggered) {
                log.error("Market price exceeded the trigger price of the open offer. " +
                                "We deactivate the open offer with ID {}. Currency: {}; offer direction: {}; " +
                                "Market price: {}; Upper price threshold : {}",
                        openOffer.getOffer().getShortId(),
                        currencyCode,
                        direction,
                        marketPrice.getPrice(),
                        MathUtils.scaleDownByPowerOf10(triggerPrice, smallestUnitExponent)
                );
                openOfferManager.deactivateOpenOffer(openOffer, () -> {
                }, errorMessage -> {
                });
            }
        }
    }

    private void onAddedOpenOffers(List<? extends OpenOffer> openOffers) {
        openOffers.forEach(openOffer -> {
            String currencyCode = openOffer.getOffer().getCurrencyCode();
            openOffersByCurrency.putIfAbsent(currencyCode, new HashSet<>());
            openOffersByCurrency.get(currencyCode).add(openOffer);

            MarketPrice marketPrice = priceFeedService.getMarketPrice(openOffer.getOffer().getCurrencyCode());
            if (marketPrice != null) {
                checkPriceThreshold(marketPrice, openOffer);
            }
        });
    }

    private void onRemovedOpenOffers(List<? extends OpenOffer> openOffers) {
        openOffers.forEach(openOffer -> {
            String currencyCode = openOffer.getOffer().getCurrencyCode();
            if (openOffersByCurrency.containsKey(currencyCode)) {
                Set<OpenOffer> set = openOffersByCurrency.get(currencyCode);
                set.remove(openOffer);
                if (set.isEmpty()) {
                    openOffersByCurrency.remove(currencyCode);
                }
            }
        });
    }
}
