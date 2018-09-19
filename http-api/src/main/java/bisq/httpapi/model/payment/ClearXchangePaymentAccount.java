package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.CLEAR_X_CHANGE_ID)
public class ClearXchangePaymentAccount extends PaymentAccount {

    @NotBlank
    public String emailOrMobileNr;

    @NotBlank
    public String holderName;

    public ClearXchangePaymentAccount() {
        super(PaymentMethod.CLEAR_X_CHANGE_ID);
    }
}
