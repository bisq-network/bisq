package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.INTERAC_E_TRANSFER_ID)
public class InteracETransferPaymentAccount extends PaymentAccount {

    @NotBlank
    public String emailOrMobileNr;

    @NotBlank
    public String holderName;

    @NotBlank
    public String question;

    @NotBlank
    public String answer;

    public InteracETransferPaymentAccount() {
        super(PaymentMethod.INTERAC_E_TRANSFER_ID);
    }
}
