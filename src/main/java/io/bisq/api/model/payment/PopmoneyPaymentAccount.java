package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.POPMONEY_ID)
public class PopmoneyPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountId;

    @NotBlank
    public String holderName;

    public PopmoneyPaymentAccount() {
        super(PaymentMethod.POPMONEY_ID);
    }
}
