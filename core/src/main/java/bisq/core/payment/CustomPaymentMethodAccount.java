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
import bisq.core.payment.payload.CustomPaymentMethodAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * The account of the custom payment method: a payment method which Bisq does not cover. The user
 * names the payment method and gives the account data as free texts.
 */
@EqualsAndHashCode(callSuper = true)
public final class CustomPaymentMethodAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = CurrencyUtil.getAllFiatCurrencies();

    public CustomPaymentMethodAccount() {
        super(PaymentMethod.CUSTOM);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new CustomPaymentMethodAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setPaymentMethodName(String paymentMethodName) {
        ((CustomPaymentMethodAccountPayload) paymentAccountPayload).setPaymentMethodName(paymentMethodName);
    }

    public String getPaymentMethodName() {
        return ((CustomPaymentMethodAccountPayload) paymentAccountPayload).getPaymentMethodName();
    }

    public void setAccountData(String accountData) {
        ((CustomPaymentMethodAccountPayload) paymentAccountPayload).setAccountData(accountData);
    }

    public String getAccountData() {
        return ((CustomPaymentMethodAccountPayload) paymentAccountPayload).getAccountData();
    }

    @Override
    public String getMessageForBuyer() {
        return "payment.custom.info.buyer";
    }

    @Override
    public String getMessageForSeller() {
        return "payment.custom.info.seller";
    }
}
