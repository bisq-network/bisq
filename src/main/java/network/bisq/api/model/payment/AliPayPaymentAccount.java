package network.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.ALI_PAY_ID)
public class AliPayPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public AliPayPaymentAccount() {
        super(PaymentMethod.ALI_PAY_ID);
    }
}
