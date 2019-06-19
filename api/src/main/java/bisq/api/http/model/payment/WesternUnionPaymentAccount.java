package bisq.api.http.model.payment;

import bisq.api.http.model.validation.CountryCode;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.WESTERN_UNION_ID)
public class WesternUnionPaymentAccount extends PaymentAccount {

    @NotBlank
    public String city;

    @CountryCode
    @NotBlank
    public String countryCode;

    @NotBlank
    public String email;

    @NotBlank
    public String holderName;

    public String state;

    public WesternUnionPaymentAccount() {
        super(PaymentMethod.WESTERN_UNION_ID);
    }
}
