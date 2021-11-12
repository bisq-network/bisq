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

import bisq.core.payment.payload.BankAccountPayload;
import bisq.core.payment.payload.AchTransferAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class AchTransferAccount extends CountryBasedPaymentAccount implements SameCountryRestrictedBankAccount {
    public AchTransferAccount() {
        super(PaymentMethod.ACH_TRANSFER);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new AchTransferAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public String getBankId() {
        return ((BankAccountPayload) paymentAccountPayload).getBankId();
    }

    @Override
    public String getCountryCode() {
        return getCountry() != null ? getCountry().code : "";
    }

    public AchTransferAccountPayload getPayload() {
        return (AchTransferAccountPayload) paymentAccountPayload;
    }

    public String getMessageForBuyer() {
        return "payment.achTransfer.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.achTransfer.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.achTransfer.info.account";
    }
}
