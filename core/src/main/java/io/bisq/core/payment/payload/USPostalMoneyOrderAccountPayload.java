/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.payment.payload;

import com.google.protobuf.Message;
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
public final class USPostalMoneyOrderAccountPayload extends PaymentAccountPayload {
    private String postalAddress;
    private String holderName;

    public USPostalMoneyOrderAccountPayload(String paymentMethod, String id, long maxTradePeriod) {
        super(paymentMethod, id, maxTradePeriod);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private USPostalMoneyOrderAccountPayload(String paymentMethod, String id,
                                             long maxTradePeriod,
                                             String postalAddress,
                                             String holderName) {
        this(paymentMethod, id, maxTradePeriod);

        this.postalAddress = postalAddress;
        this.holderName = holderName;
    }

    @Override
    public Message toProtoMessage() {
        return getPaymentAccountPayloadBuilder()
                .setUSPostalMoneyOrderAccountPayload(PB.USPostalMoneyOrderAccountPayload.newBuilder()
                        .setPostalAddress(postalAddress)
                        .setHolderName(holderName))
                .build();
    }

    public static USPostalMoneyOrderAccountPayload fromProto(PB.PaymentAccountPayload proto) {
        return new USPostalMoneyOrderAccountPayload(proto.getPaymentMethodId(),
                proto.getId(),
                proto.getMaxTradePeriod(),
                proto.getUSPostalMoneyOrderAccountPayload().getPostalAddress(),
                proto.getUSPostalMoneyOrderAccountPayload().getHolderName());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getPaymentDetails() {
        return "US Postal Money Order - Holder name: " + holderName + ", postal address: " + postalAddress;
    }


    @Override
    public String getPaymentDetailsForTradePopup() {
        return "Holder name: " + holderName + "\n" +
                "Postal address: " + postalAddress;
    }
}
