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

package io.bisq.payload.payment;

import io.bisq.app.Version;
import io.bisq.payload.Payload;

public abstract class PaymentAccountContractData implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    protected final String paymentMethodName;
    protected final String id;
    protected final long maxTradePeriod;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    PaymentAccountContractData(String paymentMethodName, String id, long maxTradePeriod) {
        this.paymentMethodName = paymentMethodName;
        this.id = id;
        this.maxTradePeriod = maxTradePeriod;
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

    public long getMaxTradePeriod() {
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
        return !(id != null ? !id.equals(that.id) : that.id != null);

    }

    @Override
    public int hashCode() {
        int result = paymentMethodName != null ? paymentMethodName.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (int) (maxTradePeriod ^ (maxTradePeriod >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "PaymentAccountContractData{" +
                "paymentMethodName='" + paymentMethodName + '\'' +
                ", id='" + id + '\'' +
                ", maxTradePeriod=" + maxTradePeriod +
                '}';
    }
}
