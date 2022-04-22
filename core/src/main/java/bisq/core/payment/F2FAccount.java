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

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public final class F2FAccount extends CountryBasedPaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = CurrencyUtil.getAllFiatCurrencies();

    public F2FAccount() {
        super(PaymentMethod.F2F);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new F2FAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setContact(String contact) {
        ((F2FAccountPayload) paymentAccountPayload).setContact(contact);
    }

    public String getContact() {
        return ((F2FAccountPayload) paymentAccountPayload).getContact();
    }

    public void setCity(String city) {
        ((F2FAccountPayload) paymentAccountPayload).setCity(city);
    }

    public String getCity() {
        return ((F2FAccountPayload) paymentAccountPayload).getCity();
    }

    public void setExtraInfo(String extraInfo) {
        ((F2FAccountPayload) paymentAccountPayload).setExtraInfo(extraInfo);
    }

    public String getExtraInfo() {
        return ((F2FAccountPayload) paymentAccountPayload).getExtraInfo();
    }
}
