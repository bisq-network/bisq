package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import bisq.httpapi.model.validation.CountryCode;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.MONEY_GRAM_ID)
public class MoneyGramPaymentAccount extends PaymentAccount {

    @NotBlank
    public String holderName;

    @CountryCode
    @NotBlank
    public String countryCode;

    public String state;

    @NotBlank
    public String email;

    public MoneyGramPaymentAccount() {
        super(PaymentMethod.MONEY_GRAM_ID);
    }
}
