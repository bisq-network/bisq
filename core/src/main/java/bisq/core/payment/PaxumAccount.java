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
import bisq.core.payment.payload.PaxumAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
public final class PaxumAccount extends PaymentAccount {

    // https://github.com/bisq-network/growth/issues/235
    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AUD"),
            new FiatCurrency("CAD"),
            new FiatCurrency("CHF"),
            new FiatCurrency("CZK"),
            new FiatCurrency("DKK"),
            new FiatCurrency("EUR"),
            new FiatCurrency("GBP"),
            new FiatCurrency("HUF"),
            new FiatCurrency("IDR"),
            new FiatCurrency("INR"),
            new FiatCurrency("NOK"),
            new FiatCurrency("NZD"),
            new FiatCurrency("PLN"),
            new FiatCurrency("RON"),
            new FiatCurrency("SEK"),
            new FiatCurrency("THB"),
            new FiatCurrency("USD"),
            new FiatCurrency("ZAR")
    );

    public PaxumAccount() {
        super(PaymentMethod.PAXUM);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new PaxumAccountPayload(paymentMethod.getId(), id);
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setEmail(String accountId) {
        ((PaxumAccountPayload) paymentAccountPayload).setEmail(accountId);
    }

    public String getEmail() {
        return ((PaxumAccountPayload) paymentAccountPayload).getEmail();
    }
}
