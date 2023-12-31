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

package bisq.core.offer.bisq_v1;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.mempool.FeeValidationStatus;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;

import bisq.common.util.MathUtils;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.ListChangeListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.roundDoubleToLong;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;


@Slf4j
@Singleton
public class TriggerPriceService {
    private final P2PService p2PService;
    private final OpenOfferManager openOfferManager;
    private final MempoolService mempoolService;
    private final PriceFeedService priceFeedService;
    private final Map<String, Set<OpenOffer>> openOffersByCurrency = new HashMap<>();
    private Consumer<String> offerDisabledHandler;
    public final IntegerProperty updateCounter = new SimpleIntegerProperty(0);

    @Inject
    public TriggerPriceService(P2PService p2PService,
                               OpenOfferManager openOfferManager,
                               MempoolService mempoolService,
                               PriceFeedService priceFeedService) {
        this.p2PService = p2PService;
        this.openOfferManager = openOfferManager;
        this.mempoolService = mempoolService;
        this.priceFeedService = priceFeedService;
    }

    public void onAllServicesInitialized(Consumer<String> offerDisabledHandler) {
        this.offerDisabledHandler = offerDisabledHandler;
        if (p2PService.isBootstrapped()) {
            onBootstrapComplete();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onDataReceived() {
                    onBootstrapComplete();
                }
            });
        }
    }

    private void onBootstrapComplete() {
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

        priceFeedService.updateCounterProperty().addListener((observable, oldValue, newValue) -> onPriceFeedChanged(false));
        onPriceFeedChanged(true);
    }

    private void onPriceFeedChanged(boolean bootstrapping) {
        openOffersByCurrency.keySet().stream()
                .map(priceFeedService::getMarketPrice)
                .filter(Objects::nonNull)
                .filter(marketPrice -> openOffersByCurrency.containsKey(marketPrice.getCurrencyCode()))
                .forEach(marketPrice -> {
                    openOffersByCurrency.get(marketPrice.getCurrencyCode()).stream()
                            .filter(openOffer -> !openOffer.isDeactivated())
                            .forEach(openOffer -> {
                                checkPriceThreshold(marketPrice, openOffer);
                                if (!bootstrapping) {
                                    maybeCheckOfferFee(openOffer);
                                }
                            });
                });
    }

    public static boolean wasTriggered(MarketPrice marketPrice, OpenOffer openOffer) {
        Price price = openOffer.getOffer().getPrice();
        if (price == null || marketPrice == null) {
            return false;
        }

        String currencyCode = openOffer.getOffer().getCurrencyCode();
        boolean cryptoCurrency = CurrencyUtil.isCryptoCurrency(currencyCode);
        int smallestUnitExponent = cryptoCurrency ?
                Altcoin.SMALLEST_UNIT_EXPONENT :
                Fiat.SMALLEST_UNIT_EXPONENT;
        long marketPriceAsLong = roundDoubleToLong(
                scaleUpByPowerOf10(marketPrice.getPrice(), smallestUnitExponent));
        long triggerPrice = openOffer.getTriggerPrice();
        if (triggerPrice <= 0) {
            return false;
        }

        OfferDirection direction = openOffer.getOffer().getDirection();
        boolean isSellOffer = direction == OfferDirection.SELL;
        boolean condition = isSellOffer && !cryptoCurrency || !isSellOffer && cryptoCurrency;
        return condition ?
                marketPriceAsLong < triggerPrice :
                marketPriceAsLong > triggerPrice;
    }

    private void checkPriceThreshold(MarketPrice marketPrice, OpenOffer openOffer) {
        Offer offer = openOffer.getOffer();
        if (offer.isBsqSwapOffer()) {
            return;
        }

        if (wasTriggered(marketPrice, openOffer)) {
            String currencyCode = offer.getCurrencyCode();
            int smallestUnitExponent = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                    Altcoin.SMALLEST_UNIT_EXPONENT :
                    Fiat.SMALLEST_UNIT_EXPONENT;
            long triggerPrice = openOffer.getTriggerPrice();

            log.info("Market price exceeded the trigger price of the open offer.\n" +
                            "We deactivate the open offer with ID {}.\nCurrency: {};\nOffer direction: {};\n" +
                            "Market price: {};\nTrigger price: {}",
                    offer.getShortId(),
                    currencyCode,
                    offer.getDirection(),
                    marketPrice.getPrice(),
                    MathUtils.scaleDownByPowerOf10(triggerPrice, smallestUnitExponent)
            );
            deactivateOpenOffer(openOffer, Res.get("openOffer.triggered", openOffer.getOffer().getShortId()));
        }
    }

    private void maybeCheckOfferFee(OpenOffer openOffer) {
        Offer offer = openOffer.getOffer();
        if (offer.isBsqSwapOffer()) {
            return;
        }

        if (openOffer.getState() == OpenOffer.State.AVAILABLE) {
            // check the offer fee if it has not been done before
            OfferPayload offerPayload = offer.getOfferPayload().orElseThrow();
            if (openOffer.getFeeValidationStatus() == FeeValidationStatus.NOT_CHECKED_YET &&
                    mempoolService.canRequestBeMade(offerPayload)) {
                mempoolService.validateOfferMakerTx(offerPayload, (txValidator -> {
                    openOffer.setFeeValidationStatus(txValidator.getStatus());
                    if (openOffer.getFeeValidationStatus().fail()) {
                        deactivateOpenOffer(openOffer, Res.get("openOffer.deactivated.feeValidationIssue",
                                openOffer.getOffer().getShortId(), openOffer.getFeeValidationStatus()));
                    }
                }));
            }
        }
    }

    private void deactivateOpenOffer(OpenOffer openOffer, String message) {
        openOfferManager.deactivateOpenOffer(openOffer, () -> { }, errorMessage -> { });
        log.info(message);
        if (offerDisabledHandler != null) {
            offerDisabledHandler.accept(message);   // shows notification on screen
        }
        // tells the UI layer (Open Offers View) to update its contents
        updateCounter.set(updateCounter.get() + 1);
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
                set.removeIf(OpenOffer::isCanceled);
                if (set.isEmpty()) {
                    openOffersByCurrency.remove(currencyCode);
                }
            }
        });
    }
}
