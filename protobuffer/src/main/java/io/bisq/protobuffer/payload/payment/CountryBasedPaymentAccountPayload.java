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

package io.bisq.protobuffer.payload.payment;

import io.bisq.common.app.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public abstract class CountryBasedPaymentAccountPayload extends PaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    protected String countryCode = "";

    CountryBasedPaymentAccountPayload(String paymentMethodName, String id, long maxTradePeriod) {
        super(paymentMethodName, id, maxTradePeriod);
    }

    public String getPaymentDetails() {
        throw new NotImplementedException();
    }

    abstract public String getPaymentDetails(Locale locale);

    /**
     * needs Locale for country based
     */
    public String getPaymentDetailsForTradePopup() {
        throw new NotImplementedException();
    }

    abstract public String getPaymentDetailsForTradePopup(Locale locale);
}
