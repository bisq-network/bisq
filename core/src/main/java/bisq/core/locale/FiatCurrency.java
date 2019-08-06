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

package bisq.core.locale;


import com.google.protobuf.Message;

import java.util.Currency;
import java.util.Locale;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString
@Getter
public final class FiatCurrency extends TradeCurrency {
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
        super(currency.getCurrencyCode(), currency.getDisplayName(locale));
        this.currency = currency;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Message toProtoMessage() {
        protobuf.Currency.Builder currencyBuilder = protobuf.Currency.newBuilder().setCurrencyCode(currency.getCurrencyCode());
        protobuf.FiatCurrency.Builder fiatCurrencyBuilder = protobuf.FiatCurrency.newBuilder().setCurrency(currencyBuilder);
        return getTradeCurrencyBuilder()
                .setFiatCurrency(fiatCurrencyBuilder)
                .build();
    }

    public static FiatCurrency fromProto(protobuf.TradeCurrency proto) {
        return new FiatCurrency(proto.getCode());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static Locale getLocale() {
        return GlobalSettings.getLocale();
    }

    @Override
    public String getDisplayPrefix() {
        return PREFIX;
    }

}
