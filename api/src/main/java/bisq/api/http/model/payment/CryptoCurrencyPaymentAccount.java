package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.BLOCK_CHAINS_ID)
public class CryptoCurrencyPaymentAccount extends PaymentAccount {

    @NotBlank
    public String address;

    public CryptoCurrencyPaymentAccount() {
        super(PaymentMethod.BLOCK_CHAINS_ID);
    }
}
