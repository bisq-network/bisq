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

package io.bisq.messages.payment.payload;

import io.bisq.app.Version;
import io.bitsquare.common.wire.proto.Messages;

public final class ChaseQuickPayAccountContractData extends PaymentAccountContractData {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String email;
    private String holderName;

    public ChaseQuickPayAccountContractData(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public ChaseQuickPayAccountContractData(String paymentMethod, String id, long maxTradePeriod, String email,
                                            String holderName) {
        this(paymentMethod, id, maxTradePeriod);
        setEmail(email);
        setHolderName(holderName);
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
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
    public Messages.PaymentAccountContractData toProtoBuf() {
        Messages.ChaseQuickPayAccountContractData.Builder chaseQuickPayAccountContractData =
                Messages.ChaseQuickPayAccountContractData.newBuilder()
                        .setEmail(email)
                        .setHolderName(holderName);
        Messages.PaymentAccountContractData.Builder paymentAccountContractData =
                Messages.PaymentAccountContractData.newBuilder()
                        .setId(id)
                        .setPaymentMethodName(paymentMethodName)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setChaseQuickPayAccountContractData(chaseQuickPayAccountContractData);
        return paymentAccountContractData.build();
    }
}
