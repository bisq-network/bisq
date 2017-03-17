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

package io.bisq.wire.payload.payment;

import io.bisq.common.app.Version;
import io.bisq.wire.proto.Messages;

public final class CryptoCurrencyAccountPayload extends PaymentAccountPayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private String address;

    public CryptoCurrencyAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }

    public CryptoCurrencyAccountPayload(String paymentMethod, String id, long maxTradePeriod, String address) {
        super(paymentMethod, id, maxTradePeriod);
        this.address = address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public String getPaymentDetails() {
        return "Receivers altcoin address: " + address;
    }

    @Override
    public String getPaymentDetailsForTradePopup() {
        return getPaymentDetails();
    }

    @Override
    public Messages.PaymentAccountPayload toProtoBuf() {
        Messages.CryptoCurrencyAccountPayload.Builder cryptoCurrencyAccountPayload =
                Messages.CryptoCurrencyAccountPayload.newBuilder().setAddress(address);
        Messages.PaymentAccountPayload.Builder paymentAccountPayload =
                Messages.PaymentAccountPayload.newBuilder()
                        .setId(id)
                        .setPaymentMethodId(paymentMethodId)
                        .setMaxTradePeriod(maxTradePeriod)
                        .setCryptoCurrencyAccountPayload(cryptoCurrencyAccountPayload);
        return paymentAccountPayload.build();
    }
}
