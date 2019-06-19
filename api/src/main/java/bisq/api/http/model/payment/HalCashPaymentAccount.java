package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.HAL_CASH_ID)
public class HalCashPaymentAccount extends PaymentAccount {

    @NotBlank
    public String mobileNr;

    public HalCashPaymentAccount() {
        super(PaymentMethod.HAL_CASH_ID);
    }
}
