package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.FASTER_PAYMENTS_ID)
public class FasterPaymentsPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    @NotBlank
    public String sortCode;

    public FasterPaymentsPaymentAccount() {
        super(PaymentMethod.FASTER_PAYMENTS_ID);
    }
}
