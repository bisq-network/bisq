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

import bisq.core.payment.payload.VerseAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class VerseAccount extends PaymentAccount {
    public VerseAccount() {
        super(PaymentMethod.VERSE);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new VerseAccountPayload(paymentMethod.getId(), id);
    }

    public void setHolderName(String accountId) {
        ((VerseAccountPayload) paymentAccountPayload).setHolderName(accountId);
    }

    public String getHolderName() {
        return ((VerseAccountPayload) paymentAccountPayload).getHolderName();
    }

    public String getMessageForBuyer() {
        return "payment.verse.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.verse.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.verse.info.account";
    }
}
