package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.PERFECT_MONEY_ID)
public class PerfectMoneyPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public PerfectMoneyPaymentAccount() {
        super(PaymentMethod.PERFECT_MONEY_ID);
    }
}
