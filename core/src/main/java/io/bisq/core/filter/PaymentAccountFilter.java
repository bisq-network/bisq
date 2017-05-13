package io.bisq.core.filter;

import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.generated.protobuffer.PB;
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
    public PB.PaymentAccountFilter toProtoMessage() {
        return PB.PaymentAccountFilter.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .setGetMethodName(getMethodName)
                .setValue(value)
                .build();
    }

    public static PaymentAccountFilter fromProto(PB.PaymentAccountFilter proto) {
        return new PaymentAccountFilter(proto.getPaymentMethodId(),
                proto.getGetMethodName(),
                proto.getValue());
    }
}
