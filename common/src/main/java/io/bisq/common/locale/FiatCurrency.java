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

package io.bisq.common.locale;

import com.google.protobuf.Message;
import io.bisq.common.GlobalSettings;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Currency;
import java.util.Locale;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
public final class FiatCurrency extends TradeCurrency {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // http://boschista.deviantart.com/journal/Cool-ASCII-Symbols-214218618
    private final static String PREFIX = "★ ";

    private final Currency currency;

    public FiatCurrency(String currencyCode) {
        this(Currency.getInstance(currencyCode), getLocale());
    }

    @SuppressWarnings("WeakerAccess")
    public FiatCurrency(Currency currency) {
        this(currency, getLocale());
    }

    @SuppressWarnings("WeakerAccess")
    public FiatCurrency(Currency currency, Locale locale) {
        super(currency.getCurrencyCode(), currency.getDisplayName(locale), currency.getSymbol());
        this.currency = currency;
    }

    private static Locale getLocale() {
        return GlobalSettings.getLocale();
    }

    @Override
    public String getDisplayPrefix() {
        return PREFIX;
    }

    @Override
    public Message toProtobuf() {
        PB.Currency.Builder currencyBuilder = PB.Currency.newBuilder().setCurrencyCode(currency.getCurrencyCode());
        PB.FiatCurrency.Builder fiatCurrencyBuilder = PB.FiatCurrency.newBuilder().setCurrency(currencyBuilder);

        PB.TradeCurrency.Builder builder = PB.TradeCurrency.newBuilder()
                .setCode(code)
                .setName(name)
                .setFiatCurrency(fiatCurrencyBuilder);
        Optional.ofNullable(symbol).ifPresent(builder::setSymbol);
        return builder.build();
    }
}
