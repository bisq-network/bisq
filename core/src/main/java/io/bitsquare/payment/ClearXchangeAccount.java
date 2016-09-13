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
import io.bitsquare.locale.FiatCurrency;

public final class ClearXchangeAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public ClearXchangeAccount() {
        super(PaymentMethod.CLEAR_X_CHANGE);
        setSingleTradeCurrency(new FiatCurrency("USD"));
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new ClearXchangeAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setEmailOrMobileNr(String mobileNr) {
        ((ClearXchangeAccountContractData) contractData).setEmailOrMobileNr(mobileNr);
    }

    public String getEmailOrMobileNr() {
        return ((ClearXchangeAccountContractData) contractData).getEmailOrMobileNr();
    }

    public void setHolderName(String holderName) {
        ((ClearXchangeAccountContractData) contractData).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((ClearXchangeAccountContractData) contractData).getHolderName();
    }
}
