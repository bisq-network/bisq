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

package bisq.core.api.model;

import bisq.core.payment.payload.CryptoCurrencyAccountPayload;
import bisq.core.payment.payload.InstantCryptoCurrencyPayload;
import bisq.core.payment.payload.PaymentAccountPayload;

import bisq.common.Payload;

import java.util.Optional;
import java.util.function.Supplier;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class PaymentAccountPayloadInfo implements Payload {

    private final String id;
    private final String paymentMethodId;
    @Nullable
    private final String address;

    public PaymentAccountPayloadInfo(String id,
                                     String paymentMethodId,
                                     @Nullable String address) {
        this.id = id;
        this.paymentMethodId = paymentMethodId;
        this.address = address;
    }

    public static PaymentAccountPayloadInfo toPaymentAccountPayloadInfo(
            @Nullable PaymentAccountPayload paymentAccountPayload) {
        if (paymentAccountPayload == null)
            return emptyPaymentAccountPayload.get();

        Optional<String> address = Optional.empty();
        if (paymentAccountPayload instanceof CryptoCurrencyAccountPayload)
            address = Optional.of(((CryptoCurrencyAccountPayload) paymentAccountPayload).getAddress());
        else if (paymentAccountPayload instanceof InstantCryptoCurrencyPayload)
            address = Optional.of(((InstantCryptoCurrencyPayload) paymentAccountPayload).getAddress());

        return new PaymentAccountPayloadInfo(paymentAccountPayload.getId(),
                paymentAccountPayload.getPaymentMethodId(),
                address.orElse(""));
    }

    // For transmitting TradeInfo messages when the contract or the contract's payload is not yet available.
    public static Supplier<PaymentAccountPayloadInfo> emptyPaymentAccountPayload = () ->
            new PaymentAccountPayloadInfo("", "", "");

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static PaymentAccountPayloadInfo fromProto(bisq.proto.grpc.PaymentAccountPayloadInfo proto) {
        return new PaymentAccountPayloadInfo(proto.getId(), proto.getPaymentMethodId(), proto.getAddress());
    }

    @Override
    public bisq.proto.grpc.PaymentAccountPayloadInfo toProtoMessage() {
        return bisq.proto.grpc.PaymentAccountPayloadInfo.newBuilder()
                .setId(id)
                .setPaymentMethodId(paymentMethodId)
                .setAddress(address != null ? address : "")
                .build();
    }
}
