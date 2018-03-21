package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.api.model.PaymentAccount;
import io.bisq.api.model.validation.CountryCode;
import io.bisq.core.payment.payload.PaymentMethod;
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
