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
import bisq.core.payment.payload.TransferwiseAccountPayload;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
public final class TransferwiseAccount extends PaymentAccount {

    // https://github.com/bisq-network/proposals/issues/243
    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AED"),
            new FiatCurrency("ARS"),
            new FiatCurrency("AUD"),
            new FiatCurrency("BDT"),
            new FiatCurrency("BGN"),
            new FiatCurrency("BRL"),
            new FiatCurrency("BWP"),
            new FiatCurrency("CAD"),
            new FiatCurrency("CHF"),
            new FiatCurrency("CLP"),
            new FiatCurrency("CNY"),
            new FiatCurrency("COP"),
            new FiatCurrency("CRC"),
            new FiatCurrency("CZK"),
            new FiatCurrency("DKK"),
            new FiatCurrency("EGP"),
            new FiatCurrency("EUR"),
            new FiatCurrency("FJD"),
            new FiatCurrency("GBP"),
            new FiatCurrency("GEL"),
            new FiatCurrency("GHS"),
            new FiatCurrency("HKD"),
            new FiatCurrency("HRK"),
            new FiatCurrency("HUF"),
            new FiatCurrency("IDR"),
            new FiatCurrency("ILS"),
            new FiatCurrency("INR"),
            new FiatCurrency("JPY"),
            new FiatCurrency("KES"),
            new FiatCurrency("KRW"),
            new FiatCurrency("LKR"),
            new FiatCurrency("MAD"),
            new FiatCurrency("MXN"),
            new FiatCurrency("MYR"),
            new FiatCurrency("NOK"),
            new FiatCurrency("NPR"),
            new FiatCurrency("NZD"),
            new FiatCurrency("PEN"),
            new FiatCurrency("PHP"),
            new FiatCurrency("PKR"),
            new FiatCurrency("PLN"),
            new FiatCurrency("RON"),
            new FiatCurrency("RUB"),
            new FiatCurrency("SEK"),
            new FiatCurrency("SGD"),
            new FiatCurrency("THB"),
            new FiatCurrency("TRY"),
            new FiatCurrency("UAH"),
            new FiatCurrency("UGX"),
            new FiatCurrency("UYU"),
            new FiatCurrency("VND"),
            new FiatCurrency("XOF"),
            new FiatCurrency("ZAR"),
            new FiatCurrency("ZMW")
    );

    public TransferwiseAccount() {
        super(PaymentMethod.TRANSFERWISE);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new TransferwiseAccountPayload(paymentMethod.getId(), id);
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setEmail(String accountId) {
        ((TransferwiseAccountPayload) paymentAccountPayload).setEmail(accountId);
    }

    public String getEmail() {
        return ((TransferwiseAccountPayload) paymentAccountPayload).getEmail();
    }

    public void setHolderName(String holderName) {
        ((TransferwiseAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((TransferwiseAccountPayload) paymentAccountPayload).getHolderName();
    }
}
