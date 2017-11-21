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

package io.bisq.core.payment;

import io.bisq.common.locale.FiatCurrency;
import io.bisq.core.payment.payload.FasterPaymentsAccountPayload;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class FasterPaymentsAccount extends PaymentAccount {
    public FasterPaymentsAccount() {
        super(PaymentMethod.FASTER_PAYMENTS);
        setSingleTradeCurrency(new FiatCurrency("GBP"));
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new FasterPaymentsAccountPayload(paymentMethod.getId(), id);
    }

    public void setSortCode(String value) {
        ((FasterPaymentsAccountPayload) paymentAccountPayload).setSortCode(value);
    }

    public String getSortCode() {
        return ((FasterPaymentsAccountPayload) paymentAccountPayload).getSortCode();
    }

    public void setAccountNr(String value) {
        ((FasterPaymentsAccountPayload) paymentAccountPayload).setAccountNr(value);
    }

    public String getAccountNr() {
        return ((FasterPaymentsAccountPayload) paymentAccountPayload).getAccountNr();
    }
}
