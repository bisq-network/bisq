package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.SAME_BANK_ID)
public class SameBankAccountPaymentAccount extends PaymentAccount {

    @NotBlank
    public String accountNr;

    public String accountType;

    @NotBlank
    public String bankId;

    @NotBlank
    public String bankName;

    @NotBlank
    public String branchId;

    @NotBlank
    public String countryCode;

    @NotBlank
    public String holderName;

    public String holderTaxId;

    public SameBankAccountPaymentAccount() {
        super(PaymentMethod.SAME_BANK_ID);
    }
}
