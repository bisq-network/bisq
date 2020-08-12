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

package bisq.core.notifications.alerts.price;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;

import bisq.common.util.MathUtils;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PriceAlert {
    private final PriceFeedService priceFeedService;
    private final MobileNotificationService mobileNotificationService;
    private final User user;

    @Inject
    public PriceAlert(PriceFeedService priceFeedService, MobileNotificationService mobileNotificationService, User user) {
        this.priceFeedService = priceFeedService;
        this.user = user;
        this.mobileNotificationService = mobileNotificationService;
    }

    public void onAllServicesInitialized() {
        priceFeedService.updateCounterProperty().addListener((observable, oldValue, newValue) -> update());
    }

    private void update() {
        if (user.getPriceAlertFilter() != null) {
            PriceAlertFilter filter = user.getPriceAlertFilter();
            String currencyCode = filter.getCurrencyCode();
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            if (marketPrice != null) {
                int exp = CurrencyUtil.isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
                double priceAsDouble = marketPrice.getPrice();
                long priceAsLong = MathUtils.roundDoubleToLong(MathUtils.scaleUpByPowerOf10(priceAsDouble, exp));
                String currencyName = CurrencyUtil.getNameByCode(currencyCode);
                if (priceAsLong > filter.getHigh() || priceAsLong < filter.getLow()) {
                    String msg = Res.get("account.notifications.priceAlert.message.msg",
                            currencyName,
                            FormattingUtils.formatMarketPrice(priceAsDouble, currencyCode),
                            CurrencyUtil.getCurrencyPair(currencyCode));
                    MobileMessage message = new MobileMessage(Res.get("account.notifications.priceAlert.message.title", currencyName),
                            msg,
                            MobileMessageType.PRICE);
                    log.error(msg);
                    try {
                        mobileNotificationService.sendMessage(message);

                        // If an alert got triggered we remove the filter.
                        user.removePriceAlertFilter();
                    } catch (Exception e) {
                        log.error(e.toString());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static MobileMessage getTestMsg() {
        String currencyCode = "USD";
        String currencyName = CurrencyUtil.getNameByCode(currencyCode);
        String msg = Res.get("account.notifications.priceAlert.message.msg",
                currencyName,
                "6023.34",
                "BTC/USD");
        return new MobileMessage(Res.get("account.notifications.priceAlert.message.title", currencyName),
                msg,
                MobileMessageType.PRICE);
    }
}
