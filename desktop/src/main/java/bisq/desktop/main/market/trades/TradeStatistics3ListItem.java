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

package bisq.desktop.main.market.trades;

import bisq.desktop.util.DisplayUtils;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import lombok.experimental.Delegate;

import org.jetbrains.annotations.Nullable;

public class TradeStatistics3ListItem {
    @Delegate
    private final TradeStatistics3 tradeStatistics3;
    private final CoinFormatter coinFormatter;
    private final boolean showAllTradeCurrencies;

    public TradeStatistics3ListItem(@Nullable TradeStatistics3 tradeStatistics3,
                                    CoinFormatter coinFormatter,
                                    boolean showAllTradeCurrencies) {
        this.tradeStatistics3 = tradeStatistics3;
        this.coinFormatter = coinFormatter;
        this.showAllTradeCurrencies = showAllTradeCurrencies;
    }

    public String getDateString() {
        return tradeStatistics3 != null ? DisplayUtils.formatDateTime(tradeStatistics3.getDate()) : "";
    }

    public String getMarket() {
        return tradeStatistics3 != null ? CurrencyUtil.getCurrencyPair(tradeStatistics3.getCurrency()) : "";
    }

    public String getPriceString() {
        return tradeStatistics3 != null ? FormattingUtils.formatPrice(tradeStatistics3.getTradePrice()) : "";
    }

    public String getVolumeString() {
        if (tradeStatistics3 == null) {
            return "";
        }
        return showAllTradeCurrencies ?
                DisplayUtils.formatVolumeWithCode(tradeStatistics3.getTradeVolume()) :
                DisplayUtils.formatVolume(tradeStatistics3.getTradeVolume());
    }

    public String getPaymentMethodString() {
        return tradeStatistics3 != null ? Res.get(tradeStatistics3.getPaymentMethod()) : "";
    }

    public String getAmountString() {
        return tradeStatistics3 != null ? coinFormatter.formatCoin(tradeStatistics3.getTradeAmount(), 4) : "";
    }
}
