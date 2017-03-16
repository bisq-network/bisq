/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.locale;

import io.bisq.app.Version;

import java.util.Currency;
import java.util.Locale;

public final class FiatCurrency extends TradeCurrency {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
    private final static String PREFIX = "★ ";

    private static Locale defaultLocale;

    public static void setDefaultLocale(Locale defaultLocale) {
        FiatCurrency.defaultLocale = defaultLocale;
    }
    
    private final Currency currency;

    public FiatCurrency(String currencyCode) {
        this(currencyCode, defaultLocale);
    }

    public FiatCurrency(String currencyCode, Locale locale) {
        this(Currency.getInstance(currencyCode), locale);
    }

    @SuppressWarnings("WeakerAccess")
    public FiatCurrency(Currency currency) {
        this(currency, defaultLocale);
    }

    @SuppressWarnings("WeakerAccess")
    public FiatCurrency(Currency currency, Locale locale) {
        super(currency.getCurrencyCode(), currency.getDisplayName(locale), currency.getSymbol());
        this.currency = currency;
    }

    public Currency getCurrency() {
        return currency;
    }

    @Override
    public String getDisplayPrefix() {
        return PREFIX;
    }

    @Override
    public String toString() {
        return "FiatCurrency{" +
                "currency=" + currency +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                '}';
    }
}
