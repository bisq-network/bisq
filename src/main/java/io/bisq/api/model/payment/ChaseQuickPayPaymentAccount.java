package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.api.model.PaymentAccount;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.CHASE_QUICK_PAY_ID)
public class ChaseQuickPayPaymentAccount extends PaymentAccount {

    @NotBlank
    public String email;

    @NotBlank
    public String holderName;

    public ChaseQuickPayPaymentAccount() {
        super(PaymentMethod.CHASE_QUICK_PAY_ID);
    }
}
