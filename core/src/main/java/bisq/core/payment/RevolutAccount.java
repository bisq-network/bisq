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
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.RevolutAccountPayload;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public final class RevolutAccount extends PaymentAccount {

    // https://www.revolut.com/help/getting-started/exchanging-currencies/what-fiat-currencies-are-supported-for-holding-and-exchange
    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AED"),
            new FiatCurrency("AUD"),
            new FiatCurrency("BGN"),
            new FiatCurrency("CAD"),
            new FiatCurrency("CHF"),
            new FiatCurrency("CZK"),
            new FiatCurrency("DKK"),
            new FiatCurrency("EUR"),
            new FiatCurrency("GBP"),
            new FiatCurrency("HKD"),
            new FiatCurrency("HUF"),
            new FiatCurrency("ILS"),
            new FiatCurrency("ISK"),
            new FiatCurrency("JPY"),
            new FiatCurrency("MAD"),
            new FiatCurrency("MXN"),
            new FiatCurrency("NOK"),
            new FiatCurrency("NZD"),
            new FiatCurrency("PLN"),
            new FiatCurrency("QAR"),
            new FiatCurrency("RON"),
            new FiatCurrency("RSD"),
            new FiatCurrency("RUB"),
            new FiatCurrency("SAR"),
            new FiatCurrency("SEK"),
            new FiatCurrency("SGD"),
            new FiatCurrency("THB"),
            new FiatCurrency("TRY"),
            new FiatCurrency("USD"),
            new FiatCurrency("ZAR")
    );

    public RevolutAccount() {
        super(PaymentMethod.REVOLUT);
        tradeCurrencies.addAll(getSupportedCurrencies());
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new RevolutAccountPayload(paymentMethod.getId(), id);
    }

    public void setUserName(String userName) {
        revolutAccountPayload().setUserName(userName);
    }

    public String getUserName() {
        return (revolutAccountPayload()).getUserName();
    }

    public String getAccountId() {
        return (revolutAccountPayload()).getAccountId();
    }

    public boolean userNameNotSet() {
        return (revolutAccountPayload()).userNameNotSet();
    }

    public boolean hasOldAccountId() {
        return (revolutAccountPayload()).hasOldAccountId();
    }

    private RevolutAccountPayload revolutAccountPayload() {
        return (RevolutAccountPayload) paymentAccountPayload;
    }

    @Override
    public void onAddToUser() {
        super.onAddToUser();

        // At save we apply the userName to accountId in case it is empty for backward compatibility
        revolutAccountPayload().maybeApplyUserNameToAccountId();
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
