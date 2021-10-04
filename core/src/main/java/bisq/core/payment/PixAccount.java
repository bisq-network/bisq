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
import bisq.core.payment.payload.PixAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class PixAccount extends CountryBasedPaymentAccount {
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

    public String getMessageForBuyer() {
        return "payment.pix.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.pix.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.pix.info.account";
    }
}
