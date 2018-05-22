package network.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import network.bisq.api.model.validation.CountryCode;
import bisq.core.payment.payload.PaymentMethod;
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
