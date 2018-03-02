package io.bisq.api.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "paymentMethod", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SepaAccountToCreate.class, name = PaymentMethod.SEPA_ID)
})
public abstract class AccountToCreate {

    @NotBlank
    public String paymentMethod;

}
