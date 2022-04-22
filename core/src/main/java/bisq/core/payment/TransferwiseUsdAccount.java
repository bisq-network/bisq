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
import bisq.core.payment.payload.TransferwiseUsdAccountPayload;

import java.util.List;

import lombok.EqualsAndHashCode;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(callSuper = true)
public final class TransferwiseUsdAccount extends CountryBasedPaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("USD"));

    public TransferwiseUsdAccount() {
        super(PaymentMethod.TRANSFERWISE_USD);
        // this payment method is currently restricted to United States/USD
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new TransferwiseUsdAccountPayload(paymentMethod.getId(), id);
    }

    public void setEmail(String email) {
        ((TransferwiseUsdAccountPayload) paymentAccountPayload).setEmail(email);
    }

    public String getEmail() {
        return ((TransferwiseUsdAccountPayload) paymentAccountPayload).getEmail();
    }

    public void setHolderName(String accountId) {
        ((TransferwiseUsdAccountPayload) paymentAccountPayload).setHolderName(accountId);
    }

    public String getHolderName() {
        return ((TransferwiseUsdAccountPayload) paymentAccountPayload).getHolderName();
    }

    public void setBeneficiaryAddress(String address) {
        ((TransferwiseUsdAccountPayload) paymentAccountPayload).setBeneficiaryAddress(address);
    }

    public String getBeneficiaryAddress() {
        return ((TransferwiseUsdAccountPayload) paymentAccountPayload).getBeneficiaryAddress();
    }

    public String getMessageForBuyer() {
        return "payment.transferwiseUsd.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.transferwiseUsd.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.transferwiseUsd.info.account";
    }

    @NotNull
    @Override
    public List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
