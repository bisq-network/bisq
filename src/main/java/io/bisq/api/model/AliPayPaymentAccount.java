package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.ALI_PAY_ID)
public class AliPayPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public AliPayPaymentAccount() {
        paymentMethod = PaymentMethod.ALI_PAY_ID;
    }
}
