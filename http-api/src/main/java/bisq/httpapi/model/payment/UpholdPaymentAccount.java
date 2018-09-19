package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.UPHOLD_ID)
public class UpholdPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountId;

    public UpholdPaymentAccount() {
        super(PaymentMethod.UPHOLD_ID);
    }
}
