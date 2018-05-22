package network.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.UPHOLD_ID)
public class UpholdPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountId;

    public UpholdPaymentAccount() {
        super(PaymentMethod.UPHOLD_ID);
    }
}
