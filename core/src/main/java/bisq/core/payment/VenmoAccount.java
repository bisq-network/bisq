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
import bisq.core.payment.payload.VenmoAccountPayload;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

// Removed due too high chargeback risk
// Cannot be deleted as it would break old trade history entries
@Deprecated
@EqualsAndHashCode(callSuper = true)
public final class VenmoAccount extends PaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("USD"));

    public VenmoAccount() {
        super(PaymentMethod.VENMO);
        setSingleTradeCurrency(SUPPORTED_CURRENCIES.get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new VenmoAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public void setVenmoUserName(String venmoUserName) {
        ((VenmoAccountPayload) paymentAccountPayload).setVenmoUserName(venmoUserName);
    }

    public String getVenmoUserName() {
        return ((VenmoAccountPayload) paymentAccountPayload).getVenmoUserName();
    }

    public void setHolderName(String holderName) {
        ((VenmoAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((VenmoAccountPayload) paymentAccountPayload).getHolderName();
    }
}
