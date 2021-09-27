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
import bisq.core.payment.payload.ImpsAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class ImpsAccount extends CountryBasedPaymentAccount {
    public ImpsAccount() {
        super(PaymentMethod.IMPS);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new ImpsAccountPayload(paymentMethod.getId(), id);
    }

    public String getMessageForBuyer() {
        return "payment.imps.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.imps.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.imps.info.account";
    }
}
