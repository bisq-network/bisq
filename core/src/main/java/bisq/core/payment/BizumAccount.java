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
import bisq.core.payment.payload.BizumAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class BizumAccount extends CountryBasedPaymentAccount {
    public BizumAccount() {
        super(PaymentMethod.BIZUM);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new BizumAccountPayload(paymentMethod.getId(), id);
    }

    public void setMobileNr(String mobileNr) {
        ((BizumAccountPayload) paymentAccountPayload).setMobileNr(mobileNr);
    }

    public String getMobileNr() {
        return ((BizumAccountPayload) paymentAccountPayload).getMobileNr();
    }

    public String getMessageForBuyer() {
        return "payment.bizum.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.bizum.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.bizum.info.account";
    }
}
