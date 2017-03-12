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

package io.bitsquare.messages.payment.payload;

import io.bitsquare.app.Version;

public abstract class CountryBasedPaymentAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    protected String countryCode = "";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    CountryBasedPaymentAccountContractData(String paymentMethodName, String id, long maxTradePeriod) {
        super(paymentMethodName, id, maxTradePeriod);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter, Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryCode() {
        return countryCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public String getPaymentDetails();

    abstract public String getPaymentDetailsForTradePopup();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CountryBasedPaymentAccountContractData)) return false;
        if (!super.equals(o)) return false;

        CountryBasedPaymentAccountContractData that = (CountryBasedPaymentAccountContractData) o;

        return countryCode.equals(that.countryCode);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + countryCode.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CountryBasedPaymentAccountContractData{" +
                "countryCode='" + countryCode + '\'' +
                "} " + super.toString();
    }
}
