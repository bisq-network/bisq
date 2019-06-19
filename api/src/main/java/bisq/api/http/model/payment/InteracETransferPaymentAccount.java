package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



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
