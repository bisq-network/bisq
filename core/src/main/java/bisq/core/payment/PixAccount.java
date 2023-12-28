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
import bisq.core.payment.payload.PixAccountPayload;

import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode(callSuper = true)
public final class PixAccount extends CountryBasedPaymentAccount {

    public static final List<TradeCurrency> SUPPORTED_CURRENCIES = List.of(new FiatCurrency("BRL"));

    public PixAccount() {
        super(PaymentMethod.PIX);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new PixAccountPayload(paymentMethod.getId(), id);
    }

    public void setPixKey(String pixKey) {
        ((PixAccountPayload) paymentAccountPayload).setPixKey(pixKey);
    }

    public String getPixKey() {
        return ((PixAccountPayload) paymentAccountPayload).getPixKey();
    }

    public void setHolderName(String holderName) {
        ((PixAccountPayload) paymentAccountPayload).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((PixAccountPayload) paymentAccountPayload).getHolderName();
    }

    public String getMessageForBuyer() {
        return "payment.pix.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.pix.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.pix.info.account";
    }

    @Override
    public @NonNull List<TradeCurrency> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }
}
