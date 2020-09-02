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

package bisq.core.monetary;

import bisq.core.locale.CurrencyUtil;
import bisq.core.util.ParsingUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

/**
 * Bitcoin price value with variable precision.
 * <p>
 * <br/>
 * We wrap an object implementing the {@link Monetary} interface from bitcoinj. We respect the
 * number of decimal digits of precision specified in the {@code smallestUnitExponent()}, defined in
 * those classes, like {@link Fiat} or {@link Altcoin}.
 */
public class Price extends MonetaryWrapper implements Comparable<Price> {
    private static final Logger log = LoggerFactory.getLogger(Price.class);

    /**
     * Create a new {@code Price} from specified {@code Monetary}.
     *
     * @param monetary
     */
    public Price(Monetary monetary) {
        super(monetary);
    }

    /**
     * Parse the Bitcoin {@code Price} given a {@code currencyCode} and {@code inputValue}.
     *
     * @param currencyCode The currency code to parse, e.g "USD" or "LTC".
     * @param input        The input value to parse as a String, e.g "2.54" or "-0.0001".
     * @return The parsed Price.
     */
    public static Price parse(String currencyCode, String input) {
        String cleaned = ParsingUtils.convertCharsForNumber(input);
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return new Price(Fiat.parseFiat(currencyCode, cleaned));
        else
            return new Price(Altcoin.parseAltcoin(currencyCode, cleaned));
    }

    /**
     * Parse the Bitcoin {@code Price} given a {@code currencyCode} and {@code inputValue}.
     *
     * @param currencyCode The currency code to parse, e.g "USD" or "LTC".
     * @param value        The value to parse.
     * @return The parsed Price.
     */
    public static Price valueOf(String currencyCode, long value) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return new Price(Fiat.valueOf(currencyCode, value));
        } else {
            return new Price(Altcoin.valueOf(currencyCode, value));
        }
    }

    public Volume getVolumeByAmount(Coin amount) {
        if (monetary instanceof Fiat)
            return new Volume(new ExchangeRate((Fiat) monetary).coinToFiat(amount));
        else if (monetary instanceof Altcoin)
            return new Volume(new AltcoinExchangeRate((Altcoin) monetary).coinToAltcoin(amount));
        else
            throw new IllegalStateException("Monetary must be either of type Fiat or Altcoin");
    }

    public Coin getAmountByVolume(Volume volume) {
        Monetary monetary = volume.getMonetary();
        if (monetary instanceof Fiat && this.monetary instanceof Fiat)
            return new ExchangeRate((Fiat) this.monetary).fiatToCoin((Fiat) monetary);
        else if (monetary instanceof Altcoin && this.monetary instanceof Altcoin)
            return new AltcoinExchangeRate((Altcoin) this.monetary).altcoinToCoin((Altcoin) monetary);
        else
            return Coin.ZERO;
    }

    public String getCurrencyCode() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).getCurrencyCode() : ((Fiat) monetary).getCurrencyCode();
    }

    public long getValue() {
        return monetary.getValue();
    }

    @Override
    public int compareTo(@NotNull Price other) {
        if (!this.getCurrencyCode().equals(other.getCurrencyCode()))
            return this.getCurrencyCode().compareTo(other.getCurrencyCode());
        if (this.getValue() != other.getValue())
            return this.getValue() > other.getValue() ? 1 : -1;
        return 0;
    }

    public boolean isPositive() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).isPositive() : ((Fiat) monetary).isPositive();
    }

    public Price subtract(Price other) {
        if (monetary instanceof Altcoin) {
            return new Price(((Altcoin) monetary).subtract((Altcoin) other.monetary));
        } else {
            return new Price(((Fiat) monetary).subtract((Fiat) other.monetary));
        }
    }

    public String toFriendlyString() {
        return monetary instanceof Altcoin ?
                ((Altcoin) monetary).toFriendlyString() + "/BTC" :
                ((Fiat) monetary).toFriendlyString().replace(((Fiat) monetary).currencyCode, "") + "BTC/" + ((Fiat) monetary).currencyCode;
    }

    public String toPlainString() {
        return monetary instanceof Altcoin ? ((Altcoin) monetary).toPlainString() : ((Fiat) monetary).toPlainString();
    }

    @Override
    public String toString() {
        return toPlainString();
    }
}
