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
import bisq.core.payment.payload.ClearXchangeAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public final class ClearXchangeAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("USD"));

    public ClearXchangeAccount() {
        super(PaymentMethod.CLEAR_X_CHANGE);
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new ClearXchangeAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setEmailOrMobileNr(String mobileNr) {
        ((ClearXchangeAccountPayload) paymentAccountPayload).setEmailOrMobileNr(mobileNr);
    }

    public String getEmailOrMobileNr() {
        return ((ClearXchangeAccountPayload) paymentAccountPayload).getEmailOrMobileNr();
    }

    public void setHolderName(String holderName) {
        ((ClearXchangeAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((ClearXchangeAccountPayload) paymentAccountPayload).getHolderName();
    }
}
