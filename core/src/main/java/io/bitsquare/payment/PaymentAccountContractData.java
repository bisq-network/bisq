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
import io.bitsquare.common.wire.Payload;

import javax.annotation.Nullable;

public abstract class PaymentAccountContractData implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final String paymentMethodName;
    private final String id;
    private final int maxTradePeriod;

    @Nullable
    protected String countryCode;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    PaymentAccountContractData(String paymentMethodName, String id, int maxTradePeriod) {
        this.paymentMethodName = paymentMethodName;
        this.id = id;
        this.maxTradePeriod = maxTradePeriod;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter, Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @Nullable
    public String getCountryCode() {
        return countryCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId() {
        return id;
    }

    public String getPaymentMethodName() {
        return paymentMethodName;
    }

    abstract public String getPaymentDetails();

    abstract public String getPaymentDetailsForTradePopup();

    public int getMaxTradePeriod() {
        return maxTradePeriod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentAccountContractData)) return false;

        PaymentAccountContractData that = (PaymentAccountContractData) o;

        if (maxTradePeriod != that.maxTradePeriod) return false;
        if (paymentMethodName != null ? !paymentMethodName.equals(that.paymentMethodName) : that.paymentMethodName != null)
            return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        return !(countryCode != null ? !countryCode.equals(that.countryCode) : that.countryCode != null);

    }

    @Override
    public int hashCode() {
        int result = paymentMethodName != null ? paymentMethodName.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + maxTradePeriod;
        result = 31 * result + (countryCode != null ? countryCode.hashCode() : 0);
        return result;
    }
}
