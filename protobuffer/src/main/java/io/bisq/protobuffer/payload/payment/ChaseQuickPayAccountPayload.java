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
public final class ChaseQuickPayAccountPayload extends PaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String email;
    private String holderName;

    public ChaseQuickPayAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public ChaseQuickPayAccountPayload(String paymentMethod, String id, long maxTradePeriod, String email,
                                       String holderName) {
        this(paymentMethod, id, maxTradePeriod);
        setEmail(email);
        setHolderName(holderName);
    }

    @Override
    public String getPaymentDetails() {
        return "Chase QuickPay - Holder name: " + holderName + ", email: " + email;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "Email: " + email;
    }

    @Override
    public PB.PaymentAccountPayload toProto() {
        PB.ChaseQuickPayAccountPayload.Builder chaseQuickPayAccountPayload =
                PB.ChaseQuickPayAccountPayload.newBuilder()
                        .setEmail(email)
                        .setHolderName(holderName);
        PB.PaymentAccountPayload.Builder paymentAccountPayload =
                PB.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setChaseQuickPayAccountPayload(chaseQuickPayAccountPayload);
        return paymentAccountPayload.build();
    }
}
