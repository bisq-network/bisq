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
import bisq.core.payment.payload.SbpAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public final class SbpAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("RUB"));

    public SbpAccount() {
        super(PaymentMethod.SBP);
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new SbpAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public String getMessageForAccountCreation() {
        return "payment.sbp.info.account";
    }

    public void setMobileNumber(String mobileNumber) {
        ((SbpAccountPayload) paymentAccountPayload).setMobileNumber(mobileNumber);
    }

    public String getMobileNumber() {
        return ((SbpAccountPayload) paymentAccountPayload).getMobileNumber();
    }

    public void setBankName(String bankName) {
        ((SbpAccountPayload) paymentAccountPayload).setBankName(bankName);
    }

    public String getBankName() {
        return ((SbpAccountPayload) paymentAccountPayload).getBankName();
    }

    public void setHolderName(String holderName) {
        ((SbpAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((SbpAccountPayload) paymentAccountPayload).getHolderName();
    }
}
