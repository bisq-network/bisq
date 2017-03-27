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
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@ToString
@Setter
@Getter
@Slf4j
public final class SwishAccountPayload extends PaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String mobileNr;
    private String holderName;

    public SwishAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public SwishAccountPayload(String paymentMethodName, String id, long maxTradePeriod,
                               String mobileNr, String holderName) {
        super(paymentMethodName, id, maxTradePeriod);
        this.mobileNr = mobileNr;
        this.holderName = holderName;
    }

    @Override
    public String getPaymentDetails() {
        return "Swish - Holder name: " + holderName + ", mobile no.: " + mobileNr;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "Mobile no.: " + mobileNr;
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.SwishAccountPayload.Builder thisClass =
                PB.SwishAccountPayload.newBuilder()
                        .setMobileNr(mobileNr)
                        .setHolderName(holderName);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setSwishAccountPayload(thisClass);
        return paymentAccountPayload.build();
    }
}
