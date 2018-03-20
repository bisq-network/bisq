package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "paymentMethod", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SepaPaymentAccount.class, name = PaymentMethod.SEPA_ID)
})
public class PaymentAccount {

    public String id;

    @NotBlank
    public String paymentMethod;

    @NotBlank
    public String selectedTradeCurrency;

    @NotEmpty
    public List<String> tradeCurrencies = new ArrayList<>();

}
