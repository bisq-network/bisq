package io.bitsquare.filter;

import io.bitsquare.app.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class PaymentAccountFilter implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(PaymentAccountFilter.class);
    public final String paymentMethodId;
    public final String dataField;
    public final String value;

    public PaymentAccountFilter(String paymentMethodId, String dataField, String value) {
        this.paymentMethodId = paymentMethodId;
        this.dataField = dataField;
        this.value = value;
    }

    @Override
    public String toString() {
        return "PaymentAccountFilter{" +
                "paymentMethodId='" + paymentMethodId + '\'' +
                ", dataField='" + dataField + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
