package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.api.model.validation.CountryCode;
import bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@JsonTypeName(PaymentMethod.SEPA_INSTANT_ID)
public class SepaInstantPaymentAccount extends PaymentAccount {

    @CountryCode
    @NotBlank
    public String countryCode;

    @NotBlank
    public String holderName;

    @NotBlank
    public String bic;

    @NotBlank
    public String iban;

    @NotEmpty
    public List<String> acceptedCountries = new ArrayList<>();

    public SepaInstantPaymentAccount() {
        super(PaymentMethod.SEPA_INSTANT_ID);
    }
}
