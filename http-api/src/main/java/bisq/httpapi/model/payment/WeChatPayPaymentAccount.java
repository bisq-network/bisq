package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.WECHAT_PAY_ID)
public class WeChatPayPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public WeChatPayPaymentAccount() {
        super(PaymentMethod.WECHAT_PAY_ID);
    }
}
