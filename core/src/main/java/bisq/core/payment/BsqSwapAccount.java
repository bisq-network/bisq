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

import bisq.core.payment.payload.BsqSwapAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import java.util.Date;

import lombok.EqualsAndHashCode;

// Placeholder account for Bsq swaps. We do not hold any data here, its just used to fit into the
// standard domain. We mimic the different trade protocol as a payment method with a dedicated account.
@EqualsAndHashCode(callSuper = true)
public final class BsqSwapAccount extends PaymentAccount {
    public static final String ID = "BsqSwapAccount";

    public BsqSwapAccount() {
        super(PaymentMethod.BSQ_SWAP);
    }

    @Override
    public void init() {
        id = ID;
        creationDate = new Date().getTime();
        paymentAccountPayload = createPayload();
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new BsqSwapAccountPayload(paymentMethod.getId(), id);
    }

}
