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

import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
public final class MoneyGramAccount extends PaymentAccount {

    @Nullable
    private Country country;

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AED"),
            new FiatCurrency("ARS"),
            new FiatCurrency("AUD"),
            new FiatCurrency("BND"),
            new FiatCurrency("CAD"),
            new FiatCurrency("CHF"),
            new FiatCurrency("CZK"),
            new FiatCurrency("DKK"),
            new FiatCurrency("EUR"),
            new FiatCurrency("FJD"),
            new FiatCurrency("GBP"),
            new FiatCurrency("HKD"),
            new FiatCurrency("HUF"),
            new FiatCurrency("IDR"),
            new FiatCurrency("ILS"),
            new FiatCurrency("INR"),
            new FiatCurrency("JPY"),
            new FiatCurrency("KRW"),
            new FiatCurrency("KWD"),
            new FiatCurrency("LKR"),
            new FiatCurrency("MAD"),
            new FiatCurrency("MGA"),
            new FiatCurrency("MXN"),
            new FiatCurrency("MYR"),
            new FiatCurrency("NOK"),
            new FiatCurrency("NZD"),
            new FiatCurrency("OMR"),
            new FiatCurrency("PEN"),
            new FiatCurrency("PGK"),
            new FiatCurrency("PHP"),
            new FiatCurrency("PKR"),
            new FiatCurrency("PLN"),
            new FiatCurrency("SAR"),
            new FiatCurrency("SBD"),
            new FiatCurrency("SCR"),
            new FiatCurrency("SEK"),
            new FiatCurrency("SGD"),
            new FiatCurrency("THB"),
            new FiatCurrency("TOP"),
            new FiatCurrency("TRY"),
            new FiatCurrency("TWD"),
            new FiatCurrency("USD"),
            new FiatCurrency("VND"),
            new FiatCurrency("VUV"),
            new FiatCurrency("WST"),
            new FiatCurrency("XOF"),
            new FiatCurrency("XPF"),
            new FiatCurrency("ZAR")
    );

    public MoneyGramAccount() {
        super(PaymentMethod.MONEY_GRAM);
        tradeCurrencies.addAll(SUPPORTED_CURRENCIES);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new MoneyGramAccountPayload(paymentMethod.getId(), id);
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    @Nullable
    public Country getCountry() {
        if (country == null) {
            final String countryCode = ((MoneyGramAccountPayload) paymentAccountPayload).getCountryCode();
            CountryUtil.findCountryByCode(countryCode).ifPresent(c -> this.country = c);
        }
        return country;
    }

    public void setCountry(@NotNull Country country) {
        this.country = country;
        ((MoneyGramAccountPayload) paymentAccountPayload).setCountryCode(country.code);
    }

    public String getEmail() {
        return ((MoneyGramAccountPayload) paymentAccountPayload).getEmail();
    }

    public void setEmail(String email) {
        ((MoneyGramAccountPayload) paymentAccountPayload).setEmail(email);
    }

    public String getFullName() {
        return ((MoneyGramAccountPayload) paymentAccountPayload).getHolderName();
    }

    public void setFullName(String email) {
        ((MoneyGramAccountPayload) paymentAccountPayload).setHolderName(email);
    }

    public String getState() {
        return ((MoneyGramAccountPayload) paymentAccountPayload).getState();
    }

    public void setState(String email) {
        ((MoneyGramAccountPayload) paymentAccountPayload).setState(email);
    }
}
