package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.NATIONAL_BANK_ID)
public class NationalBankAccountPaymentAccount extends PaymentAccount {

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

    public NationalBankAccountPaymentAccount() {
        super(PaymentMethod.NATIONAL_BANK_ID);
    }
}
