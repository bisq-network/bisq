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

import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.TransferwiseAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class TransferwiseAccount extends PaymentAccount {
    public TransferwiseAccount() {
        super(PaymentMethod.TRANSFERWISE);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new TransferwiseAccountPayload(paymentMethod.getId(), id);
    }

    public void setEmail(String accountId) {
        ((TransferwiseAccountPayload) paymentAccountPayload).setEmail(accountId);
    }

    public String getEmail() {
        return ((TransferwiseAccountPayload) paymentAccountPayload).getEmail();
    }
}
