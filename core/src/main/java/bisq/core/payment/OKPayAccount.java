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

package bisq.core.payment;

import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.OKPayAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

// Cannot be deleted as it would break old trade history entries
@Deprecated
@EqualsAndHashCode(callSuper = true)
public final class OKPayAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AED"),
            new FiatCurrency("ARS"),
            new FiatCurrency("AUD"),
            new FiatCurrency("BRL"),
            new FiatCurrency("CAD"),
            new FiatCurrency("CHF"),
            new FiatCurrency("CNY"),
            new FiatCurrency("DKK"),
            new FiatCurrency("EUR"),
            new FiatCurrency("GBP"),
            new FiatCurrency("HKD"),
            new FiatCurrency("ILS"),
            new FiatCurrency("INR"),
            new FiatCurrency("JPY"),
            new FiatCurrency("KES"),
            new FiatCurrency("MXN"),
            new FiatCurrency("NOK"),
            new FiatCurrency("NZD"),
            new FiatCurrency("PHP"),
            new FiatCurrency("PLN"),
            new FiatCurrency("SEK"),
            new FiatCurrency("SGD"),
            new FiatCurrency("USD")
    );

    public OKPayAccount() {
        super(PaymentMethod.OK_PAY);

        tradeCurrencies.addAll(SUPPORTED_CURRENCIES);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new OKPayAccountPayload(paymentMethod.getId(), id);
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setAccountNr(String accountNr) {
        ((OKPayAccountPayload) paymentAccountPayload).setAccountNr(accountNr);
    }

    public String getAccountNr() {
        return ((OKPayAccountPayload) paymentAccountPayload).getAccountNr();
    }
}
