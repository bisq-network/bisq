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
import bisq.core.payment.payload.StrikeAccountPayload;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class StrikeAccount extends CountryBasedPaymentAccount {
    public StrikeAccount() {
        super(PaymentMethod.STRIKE);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new StrikeAccountPayload(paymentMethod.getId(), id);
    }

    public void setHolderName(String accountId) {
        ((StrikeAccountPayload) paymentAccountPayload).setHolderName(accountId);
    }

    public String getHolderName() {
        return ((StrikeAccountPayload) paymentAccountPayload).getHolderName();
    }

    public String getMessageForBuyer() {
        return "payment.strike.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.strike.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.strike.info.account";
    }
}
