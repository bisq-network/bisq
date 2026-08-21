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
import bisq.core.payment.payload.CelPayAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
public final class CelPayAccount extends PaymentAccount {

    // https://github.com/bisq-network/growth/issues/231
    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(
            new FiatCurrency("AUD"),
            new FiatCurrency("CAD"),
            new FiatCurrency("GBP"),
            new FiatCurrency("HKD"),
            new FiatCurrency("USD")
    );

    public CelPayAccount() {
        super(PaymentMethod.CELPAY);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new CelPayAccountPayload(paymentMethod.getId(), id);
    }

    public void setEmail(String accountId) {
        ((CelPayAccountPayload) paymentAccountPayload).setEmail(accountId);
    }

    public String getEmail() {
        return ((CelPayAccountPayload) paymentAccountPayload).getEmail();
    }

    public String getMessageForBuyer() {
        return "payment.celpay.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.celpay.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.celpay.info.account";
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
