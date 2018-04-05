package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.SWISH_ID)
public class SwishPaymentAccount extends PaymentAccount {

    @NotBlank
    public String mobileNr;

    @NotBlank
    public String holderName;

    public SwishPaymentAccount() {
        super(PaymentMethod.SWISH_ID);
    }
}
