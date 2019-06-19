package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



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
