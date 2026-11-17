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
import bisq.core.payment.payload.StableCoinAccountPayload;
import bisq.core.payment.stablecoin.StableCoin;
import bisq.core.payment.stablecoin.StableCoinPaymentRail;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * The account of all stablecoin payment methods. The trade currency is the currency the coin is
 * pegged to: an offer of 1000 USD with a stablecoin payment method is paid with 1000 units of
 * the coin on its network.
 */
@EqualsAndHashCode(callSuper = true)
public final class StableCoinAccount extends PaymentAccount {
    public static List<TradeCurrency> getSupportedCurrencies(PaymentMethod paymentMethod) {
        return List.of(new FiatCurrency(getStableCoin(paymentMethod).getPegCurrencyCode()));
    }

    public static StableCoin getStableCoin(PaymentMethod paymentMethod) {
        return StableCoinPaymentRail.find(paymentMethod.getId())
                .orElseThrow(() -> new IllegalArgumentException("Not a stablecoin payment method: " + paymentMethod.getId()))
                .getStableCoin();
    }

    public StableCoinAccount(PaymentMethod paymentMethod) {
        super(paymentMethod);
        setSingleTradeCurrency(getSupportedCurrencies().get(0));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new StableCoinAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return getSupportedCurrencies(paymentMethod);
    }

    public StableCoin getStableCoin() {
        return getStableCoin(paymentMethod);
    }

    public void setAddress(String address) {
        ((StableCoinAccountPayload) paymentAccountPayload).setAddress(address);
    }

    public String getAddress() {
        return ((StableCoinAccountPayload) paymentAccountPayload).getAddress();
    }

    @Override
    public String getMessageForBuyer() {
        return "payment.stablecoin.info.buyer";
    }

    @Override
    public String getMessageForSeller() {
        return "payment.stablecoin.info.seller";
    }
}
