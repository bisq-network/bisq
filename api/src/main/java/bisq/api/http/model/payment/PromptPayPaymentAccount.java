package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.PROMPT_PAY_ID)
public class PromptPayPaymentAccount extends PaymentAccount {

    @NotBlank
    public String promptPayId;

    public PromptPayPaymentAccount() {
        super(PaymentMethod.PROMPT_PAY_ID);
    }
}
