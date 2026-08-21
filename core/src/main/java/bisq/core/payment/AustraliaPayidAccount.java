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
import bisq.core.payment.payload.AustraliaPayidAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.NonNull;

public final class AustraliaPayidAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("AUD"));

    public AustraliaPayidAccount() {
        super(PaymentMethod.AUSTRALIA_PAYID);
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new AustraliaPayidAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public String getPayid() {
        return ((AustraliaPayidAccountPayload) paymentAccountPayload).getPayid();
    }

    public void setPayid(String payid) {
        if (payid == null) payid = "";
        ((AustraliaPayidAccountPayload) paymentAccountPayload).setPayid(payid);
    }

    public String getBankAccountName() {
        return ((AustraliaPayidAccountPayload) paymentAccountPayload).getBankAccountName();
    }

    public void setBankAccountName(String bankAccountName) {
        if (bankAccountName == null) bankAccountName = "";
        ((AustraliaPayidAccountPayload) paymentAccountPayload).setBankAccountName(bankAccountName);
    }
}
