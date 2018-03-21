package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.OK_PAY_ID)
public class OKPayPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public OKPayPaymentAccount() {
        super(PaymentMethod.OK_PAY_ID);
    }
}
