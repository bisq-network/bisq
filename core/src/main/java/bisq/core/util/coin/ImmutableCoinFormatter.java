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
