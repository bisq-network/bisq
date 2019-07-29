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

package bisq.core.filter;

import bisq.common.proto.network.NetworkPayload;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
public class PaymentAccountFilter implements NetworkPayload {
    private final String paymentMethodId;
    private final String getMethodName;
    private final String value;

    public PaymentAccountFilter(String paymentMethodId, String getMethodName, String value) {
        this.paymentMethodId = paymentMethodId;
        this.getMethodName = getMethodName;
        this.value = value;
    }

    @Override
    public protobuf.PaymentAccountFilter toProtoMessage() {
        return protobuf.PaymentAccountFilter.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .setGetMethodName(getMethodName)
                .setValue(value)
                .build();
    }

    public static PaymentAccountFilter fromProto(protobuf.PaymentAccountFilter proto) {
        return new PaymentAccountFilter(proto.getPaymentMethodId(),
                proto.getGetMethodName(),
                proto.getValue());
    }
}
