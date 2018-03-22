package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;
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
