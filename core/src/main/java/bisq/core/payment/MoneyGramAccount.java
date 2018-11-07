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
import bisq.core.locale.CurrencyUtil;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@EqualsAndHashCode(callSuper = true)
public final class MoneyGramAccount extends PaymentAccount {

    @Nullable
    private Country country;


    public MoneyGramAccount() {
        super(PaymentMethod.MONEY_GRAM);
        tradeCurrencies.addAll(CurrencyUtil.getAllMoneyGramCurrencies());
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new MoneyGramAccountPayload(paymentMethod.getId(), id);
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
