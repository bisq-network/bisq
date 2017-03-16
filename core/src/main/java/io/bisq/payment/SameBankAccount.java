/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.payment;

import io.bisq.app.Version;
import io.bisq.payload.payment.BankAccountContractData;
import io.bisq.payload.payment.PaymentAccountContractData;
import io.bisq.payload.payment.PaymentMethod;
import io.bisq.payload.payment.SameBankAccountContractData;

public final class SameBankAccount extends CountryBasedPaymentAccount implements BankNameRestrictedBankAccount, SameCountryRestrictedBankAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public SameBankAccount() {
        super(PaymentMethod.SAME_BANK);
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new SameBankAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    @Override
    public String getBankId() {
        return ((BankAccountContractData) contractData).getBankId();
    }

    @Override
    public String getCountryCode() {
        return getCountry() != null ? getCountry().code : "";
    }
}
