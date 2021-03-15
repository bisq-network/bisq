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

package bisq.core.util.coin;

import bisq.core.util.FormattingUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImmutableCoinFormatter implements CoinFormatter {

    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit,
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    @Getter
    private MonetaryFormat monetaryFormat;

    @Inject
    public ImmutableCoinFormatter(MonetaryFormat monetaryFormat) {
        this.monetaryFormat = monetaryFormat;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String formatCoin(Coin coin) {
        return formatCoin(coin, -1);
    }

    @Override
    public String formatCoin(Coin coin, boolean appendCode) {
        return appendCode ? formatCoinWithCode(coin) : formatCoin(coin);
    }

    @Override
    public String formatCoin(Coin coin, int decimalPlaces) {
        return formatCoin(coin, decimalPlaces, false, 0);
    }

    @Override
    public String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits) {
        return FormattingUtils.formatCoin(coin, decimalPlaces, decimalAligned, maxNumberOfDigits, monetaryFormat);
    }

    @Override
    public String formatCoinWithCode(Coin coin) {
        return FormattingUtils.formatCoinWithCode(coin, monetaryFormat);
    }

    @Override
    public String formatCoinWithCode(long value) {
        return FormattingUtils.formatCoinWithCode(Coin.valueOf(value), monetaryFormat);
    }
}
