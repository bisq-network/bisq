package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.CASH_APP_ID)
public class CashAppPaymentAccount extends PaymentAccount {

    @NotBlank
    public String cashTag;

    public CashAppPaymentAccount() {
        super(PaymentMethod.CASH_APP_ID);
    }
}
