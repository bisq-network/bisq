package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;

@JsonTypeName(PaymentMethod.SEPA_ID)
public class SepaPaymentAccount {

    public String accountName;
    public String countryCode;
    public String holderName;
    public String bic;
    public String iban;

}
