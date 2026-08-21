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
import bisq.core.payment.payload.CashDepositAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.List;

import lombok.NonNull;

import javax.annotation.Nullable;

public final class CashDepositAccount extends CountryBasedPaymentAccount implements SameCountryRestrictedBankAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = CurrencyUtil.getAllFiatCurrencies();

    public CashDepositAccount() {
        super(PaymentMethod.CASH_DEPOSIT);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new CashDepositAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    @Override
    public String getBankId() {
        return ((CashDepositAccountPayload) paymentAccountPayload).getBankId();
    }

    @Override
    public String getCountryCode() {
        return getCountry() != null ? getCountry().code : "";
    }

    @Nullable
    public String getRequirements() {
        return ((CashDepositAccountPayload) paymentAccountPayload).getRequirements();
    }

    public void setRequirements(String requirements) {
        ((CashDepositAccountPayload) paymentAccountPayload).setRequirements(requirements);
    }
}
