package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.REVOLUT_ID)
public class RevolutPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountId;

    public RevolutPaymentAccount() {
        super(PaymentMethod.REVOLUT_ID);
    }
}
