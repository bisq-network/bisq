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
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;

import lombok.experimental.Delegate;

import org.jetbrains.annotations.Nullable;

public class TradeStatistics3ListItem {
    @Delegate
    private final TradeStatistics3 tradeStatistics3;
    private final CoinFormatter coinFormatter;
    private final boolean showAllTradeCurrencies;
    private String dateString;
    private String market;
    private String priceString;
    private String volumeString;
    private String paymentMethodString;
    private String amountString;

    public TradeStatistics3ListItem(@Nullable TradeStatistics3 tradeStatistics3,
                                    CoinFormatter coinFormatter,
                                    boolean showAllTradeCurrencies) {
        this.tradeStatistics3 = tradeStatistics3;
        this.coinFormatter = coinFormatter;
        this.showAllTradeCurrencies = showAllTradeCurrencies;
    }

    public String getDateString() {
        if (dateString == null) {
            dateString = tradeStatistics3 != null ? DisplayUtils.formatDateTime(tradeStatistics3.getDate()) : "";
        }
        return dateString;
    }

    public String getMarket() {
        if (market == null) {
            market = tradeStatistics3 != null ? CurrencyUtil.getCurrencyPair(tradeStatistics3.getCurrency()) : "";
        }
        return market;
    }

    public String getPriceString() {
        if (priceString == null) {
            priceString = tradeStatistics3 != null ? FormattingUtils.formatPrice(tradeStatistics3.getTradePrice()) : "";
        }
        return priceString;
    }

    public String getVolumeString() {
        if (volumeString == null) {
            volumeString = tradeStatistics3 != null ? showAllTradeCurrencies ?
                    VolumeUtil.formatVolumeWithCode(tradeStatistics3.getTradeVolume()) :
                    VolumeUtil.formatVolume(tradeStatistics3.getTradeVolume())
                    : "";
        }
        return volumeString;
    }

    public String getPaymentMethodString() {
        if (paymentMethodString == null) {
            paymentMethodString = tradeStatistics3 != null ? Res.get(tradeStatistics3.getPaymentMethodId()) : "";
        }
        return paymentMethodString;
    }

    public String getAmountString() {
        if (amountString == null) {
            amountString = tradeStatistics3 != null ? coinFormatter.formatCoin(tradeStatistics3.getTradeAmount(), 4) : "";
        }
        return amountString;
    }
}
