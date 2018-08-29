package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import bisq.httpapi.model.validation.CountryCode;

import com.fasterxml.jackson.annotation.JsonTypeName;



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
