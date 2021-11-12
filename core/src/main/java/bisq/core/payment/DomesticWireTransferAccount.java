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
import bisq.core.payment.payload.DomesticWireTransferAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public final class DomesticWireTransferAccount extends CountryBasedPaymentAccount implements SameCountryRestrictedBankAccount {
    public DomesticWireTransferAccount() {
        super(PaymentMethod.DOMESTIC_WIRE_TRANSFER);
    }

    @Override
    protected PaymentAccountPayload createPayload() {
        return new DomesticWireTransferAccountPayload(paymentMethod.getId(), id);
    }

    @Override
    public String getBankId() {
        return ((BankAccountPayload) paymentAccountPayload).getBankId();
    }

    @Override
    public String getCountryCode() {
        return getCountry() != null ? getCountry().code : "";
    }

    public DomesticWireTransferAccountPayload getPayload() {
        return (DomesticWireTransferAccountPayload) paymentAccountPayload;
    }

    public String getMessageForBuyer() {
        return "payment.domesticWire.info.buyer";
    }

    public String getMessageForSeller() {
        return "payment.domesticWire.info.seller";
    }

    public String getMessageForAccountCreation() {
        return "payment.domesticWire.info.account";
    }
}
