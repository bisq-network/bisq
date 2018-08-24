package bisq.httpapi.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;



import bisq.httpapi.model.validation.CountryCode;
import org.hibernate.validator.constraints.NotBlank;

@JsonTypeName(PaymentMethod.CASH_DEPOSIT_ID)
public class CashDepositPaymentAccount extends PaymentAccount {

    public String accountNr;

    public String accountType;

    public String bankId;

    public String bankName;

    public String branchId;

    @CountryCode
    @NotBlank
    public String countryCode;

    @NotBlank
    public String holderName;

    @NotBlank
    public String holderEmail;

    public String holderTaxId;

    public String requirements;

    public CashDepositPaymentAccount() {
        super(PaymentMethod.CASH_DEPOSIT_ID);
    }
}
