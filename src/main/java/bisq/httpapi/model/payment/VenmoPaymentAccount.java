package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.VENMO_ID)
public class VenmoPaymentAccount extends PaymentAccount {

    @NotBlank
    public String holderName;

    @NotBlank
    public String venmoUserName;

    public VenmoPaymentAccount() {
        super(PaymentMethod.VENMO_ID);
    }
}
