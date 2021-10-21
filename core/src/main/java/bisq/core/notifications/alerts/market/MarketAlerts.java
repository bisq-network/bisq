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

package bisq.core.notifications.alerts.market;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferDirection;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;

import bisq.common.crypto.KeyRing;
import bisq.common.util.MathUtils;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MarketAlerts {
    private final OfferBookService offerBookService;
    private final MobileNotificationService mobileNotificationService;
    private final User user;
    private final PriceFeedService priceFeedService;
    private final KeyRing keyRing;

    @Inject
    private MarketAlerts(OfferBookService offerBookService, MobileNotificationService mobileNotificationService,
                         User user, PriceFeedService priceFeedService, KeyRing keyRing) {
        this.offerBookService = offerBookService;
        this.mobileNotificationService = mobileNotificationService;
        this.user = user;
        this.priceFeedService = priceFeedService;
        this.keyRing = keyRing;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer) {
                onOfferAdded(offer);
            }

            @Override
            public void onRemoved(Offer offer) {
            }
        });
        applyFilterOnAllOffers();
    }

    public void addMarketAlertFilter(MarketAlertFilter filter) {
        user.addMarketAlertFilter(filter);
        applyFilterOnAllOffers();
    }

    public void removeMarketAlertFilter(MarketAlertFilter filter) {
        user.removeMarketAlertFilter(filter);
    }

    public List<MarketAlertFilter> getMarketAlertFilters() {
        return user.getMarketAlertFilters();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyFilterOnAllOffers() {
        offerBookService.getOffers().forEach(this::onOfferAdded);
    }

    // We combine the offer ID and the price (either as % price or as fixed price) to get also updates for edited offers
    // % price get multiplied by 10000 to have 0.12% be converted to 12. For fixed price we have precision of 8 for
    // altcoins and precision of 4 for fiat.
    private String getAlertId(Offer offer) {
        double price = offer.isUseMarketBasedPrice() ? offer.getMarketPriceMargin() * 10000 : offer.getFixedPrice();
        String priceString = String.valueOf((long) price);
        return offer.getId() + "|" + priceString;
    }

    private void onOfferAdded(Offer offer) {
        String currencyCode = offer.getCurrencyCode();
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        Price offerPrice = offer.getPrice();
        if (marketPrice != null && offerPrice != null) {
            boolean isSellOffer = offer.getDirection() == OfferDirection.SELL;
            String shortOfferId = offer.getShortId();
            boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(currencyCode);
            String alertId = getAlertId(offer);
            user.getMarketAlertFilters().stream()
                    .filter(marketAlertFilter -> !offer.isMyOffer(keyRing))
                    .filter(marketAlertFilter -> offer.getPaymentMethod().equals(marketAlertFilter.getPaymentAccount().getPaymentMethod()))
                    .filter(marketAlertFilter -> marketAlertFilter.notContainsAlertId(alertId))
                    .forEach(marketAlertFilter -> {
                        int triggerValue = marketAlertFilter.getTriggerValue();
                        boolean isTriggerForBuyOffer = marketAlertFilter.isBuyOffer();
                        double marketPriceAsDouble1 = marketPrice.getPrice();
                        int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                                Altcoin.SMALLEST_UNIT_EXPONENT :
                                Fiat.SMALLEST_UNIT_EXPONENT;
                        double marketPriceAsDouble = MathUtils.scaleUpByPowerOf10(marketPriceAsDouble1, precision);
                        double offerPriceValue = offerPrice.getValue();
                        double ratio = offerPriceValue / marketPriceAsDouble;
                        ratio = 1 - ratio;
                        if (isFiatCurrency && isSellOffer)
                            ratio *= -1;
                        else if (!isFiatCurrency && !isSellOffer)
                            ratio *= -1;

                        ratio = ratio * 10000;
                        boolean triggered = ratio <= triggerValue;
                        if (!triggered)
                            return;

                        boolean isTriggerForBuyOfferAndTriggered = !isSellOffer && isTriggerForBuyOffer;
                        boolean isTriggerForSellOfferAndTriggered = isSellOffer && !isTriggerForBuyOffer;
                        if (isTriggerForBuyOfferAndTriggered || isTriggerForSellOfferAndTriggered) {
                            String direction = isSellOffer ? Res.get("shared.sell") : Res.get("shared.buy");
                            String marketDir;
                            if (isFiatCurrency) {
                                if (isSellOffer) {
                                    marketDir = ratio > 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                } else {
                                    marketDir = ratio < 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                }
                            } else {
                                if (isSellOffer) {
                                    marketDir = ratio < 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                } else {
                                    marketDir = ratio > 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                }
                            }

                            ratio = Math.abs(ratio);
                            String msg = Res.get("account.notifications.marketAlert.message.msg",
                                    direction,
                                    CurrencyUtil.getCurrencyPair(currencyCode),
                                    FormattingUtils.formatPrice(offerPrice),
                                    FormattingUtils.formatToPercentWithSymbol(ratio / 10000d),
                                    marketDir,
                                    Res.get(offer.getPaymentMethod().getId()),
                                    shortOfferId);
                            MobileMessage message = new MobileMessage(Res.get("account.notifications.marketAlert.message.title"),
                                    msg,
                                    shortOfferId,
                                    MobileMessageType.MARKET);
                            try {
                                boolean wasSent = mobileNotificationService.sendMessage(message);
                                if (wasSent) {
                                    // In case we have disabled alerts wasSent is false and we do not
                                    // persist the offer
                                    marketAlertFilter.addAlertId(alertId);
                                    user.requestPersistence();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }

    public static MobileMessage getTestMsg() {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return new MobileMessage(Res.get("account.notifications.marketAlert.message.title"),
                "A new 'sell BTC/USD' offer with price 6019.2744 (5.36% below market price) and payment method " +
                        "'Perfect Money' was published to the Bisq offerbook.\n" +
                        "Offer ID: wygiaw.",
                shortId,
                MobileMessageType.MARKET);
    }
}
