package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.PERFECT_MONEY_ID)
public class PerfectMoneyPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public PerfectMoneyPaymentAccount() {
        super(PaymentMethod.PERFECT_MONEY_ID);
    }
}
