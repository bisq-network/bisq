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
import bisq.core.payment.payload.InstantCryptoCurrencyPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public final class InstantCryptoCurrencyAccount extends AssetAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = new ArrayList<>(CurrencyUtil.getAllSortedCryptoCurrencies());

    public InstantCryptoCurrencyAccount() {
        super(PaymentMethod.BLOCK_CHAINS_INSTANT);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new InstantCryptoCurrencyPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
