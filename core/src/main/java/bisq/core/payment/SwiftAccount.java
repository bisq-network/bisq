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

import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SwiftAccountPayload;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

import static bisq.core.locale.CurrencyUtil.getAllSortedFiatCurrencies;
import static java.util.Comparator.comparing;

@EqualsAndHashCode(callSuper = true)
public final class SwiftAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = new ArrayList<>(getAllSortedFiatCurrencies(comparing(TradeCurrency::getCode)));

    public SwiftAccount() {
        super(PaymentMethod.SWIFT);
        tradeCurrencies.addAll(SUPPORTED_CURRENCIES);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new SwiftAccountPayload(paymentMethod.getId(), id);
    }

    public SwiftAccountPayload getPayload() {
        return ((SwiftAccountPayload) this.paymentAccountPayload);
    }

    public String getMessageForBuyer() {
        return "payment.swift.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.swift.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.swift.info.account";
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
