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
import bisq.core.payment.payload.AmazonGiftCardAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public final class AmazonGiftCardAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AUD"),
            new FiatCurrency("CAD"),
            new FiatCurrency("EUR"),
            new FiatCurrency("GBP"),
            new FiatCurrency("INR"),
            new FiatCurrency("JPY"),
            new FiatCurrency("SAR"),
            new FiatCurrency("SEK"),
            new FiatCurrency("SGD"),
            new FiatCurrency("TRY"),
            new FiatCurrency("USD")
    );

    @Nullable
    private Country country;

    public AmazonGiftCardAccount() {
        super(PaymentMethod.AMAZON_GIFT_CARD);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new AmazonGiftCardAccountPayload(paymentMethod.getId(), id);
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public String getEmailOrMobileNr() {
        return getAmazonGiftCardAccountPayload().getEmailOrMobileNr();
    }

    public void setEmailOrMobileNr(String emailOrMobileNr) {
        getAmazonGiftCardAccountPayload().setEmailOrMobileNr(emailOrMobileNr);
    }

    public boolean countryNotSet() {
        return (getAmazonGiftCardAccountPayload()).countryNotSet();
    }

    @Nullable
    public Country getCountry() {
        if (country == null) {
            final String countryCode = getAmazonGiftCardAccountPayload().getCountryCode();
            CountryUtil.findCountryByCode(countryCode).ifPresent(c -> this.country = c);
        }
        return country;
    }

    public void setCountry(@NotNull Country country) {
        this.country = country;
        getAmazonGiftCardAccountPayload().setCountryCode(country.code);
    }

    private AmazonGiftCardAccountPayload getAmazonGiftCardAccountPayload() {
        return (AmazonGiftCardAccountPayload) paymentAccountPayload;
    }
}
