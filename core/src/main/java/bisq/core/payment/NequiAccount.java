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
import bisq.core.payment.payload.NequiAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class NequiAccount extends CountryBasedPaymentAccount {
    public NequiAccount() {
        super(PaymentMethod.NEQUI);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new NequiAccountPayload(paymentMethod.getId(), id);
    }

    public void setMobileNr(String mobileNr) {
        ((NequiAccountPayload) paymentAccountPayload).setMobileNr(mobileNr);
    }

    public String getMobileNr() {
        return ((NequiAccountPayload) paymentAccountPayload).getMobileNr();
    }

    public String getMessageForBuyer() {
        return "payment.nequi.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.nequi.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.nequi.info.account";
    }
}
