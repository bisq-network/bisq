package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.api.model.PaymentAccount;
import io.bisq.api.model.validation.CountryCode;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@JsonTypeName(PaymentMethod.SEPA_ID)
public class SepaPaymentAccount extends PaymentAccount {

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

    //    TODO add accepted countries and make sure that countryCode is on that list

    public SepaPaymentAccount() {
        paymentMethod = PaymentMethod.SEPA_ID;
    }
}
