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

import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.payment.payload.WesternUnionAccountPayload;

public final class WesternUnionAccount extends CountryBasedPaymentAccount {
    public WesternUnionAccount() {
        super(PaymentMethod.WESTERN_UNION);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new WesternUnionAccountPayload(paymentMethod.getId(), id);
    }

    public String getEmail() {
        return ((WesternUnionAccountPayload) paymentAccountPayload).getEmail();
    }

    public void setEmail(String email) {
        ((WesternUnionAccountPayload) paymentAccountPayload).setEmail(email);
    }

    public String getFullName() {
        return ((WesternUnionAccountPayload) paymentAccountPayload).getHolderName();
    }

    public void setFullName(String email) {
        ((WesternUnionAccountPayload) paymentAccountPayload).setHolderName(email);
    }

    public String getCity() {
        return ((WesternUnionAccountPayload) paymentAccountPayload).getCity();
    }

    public void setCity(String email) {
        ((WesternUnionAccountPayload) paymentAccountPayload).setCity(email);
    }

    public String getState() {
        return ((WesternUnionAccountPayload) paymentAccountPayload).getState();
    }

    public void setState(String email) {
        ((WesternUnionAccountPayload) paymentAccountPayload).setState(email);
    }
}
