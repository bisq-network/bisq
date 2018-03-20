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
        @JsonSubTypes.Type(value = AliPayPaymentAccount.class, name = PaymentMethod.ALI_PAY_ID),
        @JsonSubTypes.Type(value = SepaPaymentAccount.class, name = PaymentMethod.SEPA_ID),
        @JsonSubTypes.Type(value = RevolutPaymentAccount.class, name = PaymentMethod.REVOLUT_ID)
})
public class PaymentAccount {

    public String id;

    @NotBlank
    public String accountName;

    @NotBlank
    public String paymentMethod;

    @NotBlank
    public String selectedTradeCurrency;

    @NotEmpty
    public List<String> tradeCurrencies = new ArrayList<>();

}
