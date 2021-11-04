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

import bisq.core.payment.payload.MoneseAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class MoneseAccount extends PaymentAccount {
    public MoneseAccount() {
        super(PaymentMethod.MONESE);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new MoneseAccountPayload(paymentMethod.getId(), id);
    }

    public void setHolderName(String accountId) {
        ((MoneseAccountPayload) paymentAccountPayload).setHolderName(accountId);
    }

    public String getHolderName() {
        return ((MoneseAccountPayload) paymentAccountPayload).getHolderName();
    }

    public void setMobileNr(String accountId) {
        ((MoneseAccountPayload) paymentAccountPayload).setMobileNr(accountId);
    }

    public String getMobileNr() {
        return ((MoneseAccountPayload) paymentAccountPayload).getMobileNr();
    }

    public String getMessageForBuyer() {
        return "payment.monese.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.monese.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.monese.info.account";
    }
}
