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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.util.filtering.FilterableListItem;

import bisq.core.monetary.Price;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.core.locale.CurrencyUtil.getCurrencyPair;

/**
 * We could remove that wrapper if it is not needed for additional UI only fields.
 */
public class PendingTradesListItem implements FilterableListItem {
    public static final Logger log = LoggerFactory.getLogger(PendingTradesListItem.class);
    private final CoinFormatter btcFormatter;
    private final Trade trade;

    public PendingTradesListItem(Trade trade, CoinFormatter btcFormatter) {
        this.trade = trade;
        this.btcFormatter = btcFormatter;
    }

    public Trade getTrade() {
        return trade;
    }

    public Price getPrice() {
        return trade.getPrice();
    }

    public String getPriceAsString() {
        return FormattingUtils.formatPrice(trade.getPrice());
    }

    public String getAmountAsString() {
        return btcFormatter.formatCoin(trade.getAmount());
    }

    public String getPaymentMethod() {
        return trade.getOffer().getPaymentMethodNameWithCountryCode();
    }

    public String getMarketDescription() {
        return getCurrencyPair(trade.getOffer().getCurrencyCode());
    }

    @Override
    public boolean match(String filterString) {
        if (filterString.isEmpty()) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getTrade().getId(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getAmountAsString(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getPaymentMethod(), filterString)) {
            return true;
        }
        if (StringUtils.containsIgnoreCase(getMarketDescription(), filterString)) {
            return true;
        }
        return StringUtils.containsIgnoreCase(getPriceAsString(), filterString);
    }
}
