/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.locale;

import io.bitsquare.app.Version;
import io.bitsquare.user.Preferences;

import java.io.Serializable;
import java.util.Currency;

public class FiatCurrency extends TradeCurrency implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    private final Currency currency;

    public FiatCurrency(String currencyCode) {
        this(Currency.getInstance(currencyCode));
    }

    @SuppressWarnings("WeakerAccess")
    public FiatCurrency(Currency currency) {
        super(currency.getCurrencyCode(), currency.getDisplayName(Preferences.getDefaultLocale()), currency.getSymbol());
        this.currency = currency;
    }

    public Currency getCurrency() {
        return currency;
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
