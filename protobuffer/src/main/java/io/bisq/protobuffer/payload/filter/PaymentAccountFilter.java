package io.bisq.protobuffer.payload.filter;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

@ToString
@Slf4j
public class PaymentAccountFilter implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Payload
    public final String paymentMethodId;
    public final String getMethodName;
    public final String value;

    public PaymentAccountFilter(String paymentMethodId, String getMethodName, String value) {
        this.paymentMethodId = paymentMethodId;
        this.getMethodName = getMethodName;
        this.value = value;
    }

    public PB.PaymentAccountFilter toProtoBuf() {
        return PB.PaymentAccountFilter.newBuilder()
                .setPaymentMethodId(paymentMethodId)
                .setGetMethodName(getMethodName)
                .setValue(value).build();
    }
}
