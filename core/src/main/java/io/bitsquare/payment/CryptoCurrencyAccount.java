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

package io.bitsquare.payment;

import io.bitsquare.app.Version;
import io.bitsquare.messages.payment.PaymentMethod;
import io.bitsquare.messages.payment.payload.CryptoCurrencyAccountContractData;
import io.bitsquare.messages.payment.payload.PaymentAccountContractData;

public final class CryptoCurrencyAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;


    public CryptoCurrencyAccount() {
        super(PaymentMethod.BLOCK_CHAINS);

    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new CryptoCurrencyAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setAddress(String address) {
        ((CryptoCurrencyAccountContractData) contractData).setAddress(address);
    }

    public String getAddress() {
        return ((CryptoCurrencyAccountContractData) contractData).getAddress();
    }
}
