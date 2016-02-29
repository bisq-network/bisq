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

import java.util.List;

public final class SepaAccount extends PaymentAccount {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public SepaAccount() {
        super(PaymentMethod.SEPA);
    }

    @Override
    protected PaymentAccountContractData setContractData() {
        return new SepaAccountContractData(paymentMethod.getId(), id, paymentMethod.getMaxTradePeriod());
    }

    public void setHolderName(String holderName) {
        ((SepaAccountContractData) contractData).setHolderName(holderName);
    }

    public String getHolderName() {
        return ((SepaAccountContractData) contractData).getHolderName();
    }

    public void setIban(String iban) {
        ((SepaAccountContractData) contractData).setIban(iban);
    }

    public String getIban() {
        return ((SepaAccountContractData) contractData).getIban();
    }

    public void setBic(String bic) {
        ((SepaAccountContractData) contractData).setBic(bic);
    }

    public String getBic() {
        return ((SepaAccountContractData) contractData).getBic();
    }

    public List<String> getAcceptedCountryCodes() {
        return ((SepaAccountContractData) contractData).getAcceptedCountryCodes();
    }

    public void addAcceptedCountry(String countryCode) {
        ((SepaAccountContractData) contractData).addAcceptedCountry(countryCode);
    }

    public void removeAcceptedCountry(String countryCode) {
        ((SepaAccountContractData) contractData).removeAcceptedCountry(countryCode);
    }


}
