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

import io.bitsquare.messages.app.Version;
import io.bitsquare.messages.locale.FiatCurrency;
import io.bitsquare.messages.payment.PaymentMethod;
import io.bitsquare.messages.payment.payload.PaymentAccountContractData;
import io.bitsquare.messages.payment.payload.SwishAccountContractData;

public final class SwishAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public SwishAccount() {
        super(PaymentMethod.SWISH);
        setSingleTradeCurrency(new FiatCurrency("SEK"));
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new SwishAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setMobileNr(String mobileNr) {
        ((SwishAccountContractData) contractData).setMobileNr(mobileNr);
    }

    public String getMobileNr() {
        return ((SwishAccountContractData) contractData).getMobileNr();
    }

    public void setHolderName(String holderName) {
        ((SwishAccountContractData) contractData).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((SwishAccountContractData) contractData).getHolderName();
    }
}
