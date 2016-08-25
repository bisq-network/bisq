/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.payment;

import io.bitsquare.app.Version;

public final class CashDepositAccount extends CountryBasedPaymentAccount implements SameCountryRestrictedBankAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public CashDepositAccount() {
        super(PaymentMethod.CASH_DEPOSIT);
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new CashDepositAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    @Override
    public String getBankId() {
        return ((CashDepositAccountContractData) contractData).getBankId();
    }

    @Override
    public String getCountryCode() {
        return getCountry() != null ? getCountry().code : "";
    }
}
