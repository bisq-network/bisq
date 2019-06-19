package bisq.api.http.model.payment;

import bisq.api.http.model.validation.CountryCode;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.ArrayList;
import java.util.List;



import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

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

    public SepaPaymentAccount() {
        super(PaymentMethod.SEPA_ID);
    }
}
