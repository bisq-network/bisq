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
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SwiftAccountPayload;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class SwiftAccount extends PaymentAccount {
    public SwiftAccount() {
        super(PaymentMethod.SWIFT);
        selectAllTradeCurrencies();
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

    private void selectAllTradeCurrencies() {
        List<FiatCurrency> currencyCodesSorted = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                .sorted(Comparator.comparing(TradeCurrency::getCode))
                .collect(Collectors.toList());
        tradeCurrencies.addAll(currencyCodesSorted);
    }
}
