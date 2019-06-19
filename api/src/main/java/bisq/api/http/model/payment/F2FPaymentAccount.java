package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.F2F_ID)
public class F2FPaymentAccount extends PaymentAccount {

    @NotBlank
    public String contact;

    @NotBlank
    public String city;

    public String extraInfo;

    public F2FPaymentAccount() {
        super(PaymentMethod.F2F_ID);
    }
}
