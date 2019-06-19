package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.ADVANCED_CASH_ID)
public class AdvancedCashPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public AdvancedCashPaymentAccount() {
        super(PaymentMethod.ADVANCED_CASH_ID);
    }
}
