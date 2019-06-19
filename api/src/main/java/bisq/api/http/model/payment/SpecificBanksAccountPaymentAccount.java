package bisq.api.http.model.payment;

import bisq.core.payment.payload.PaymentMethod;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.ArrayList;
import java.util.List;



import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

@JsonTypeName(PaymentMethod.SPECIFIC_BANKS_ID)
public class SpecificBanksAccountPaymentAccount extends PaymentAccount {

    @NotEmpty
    public List<String> acceptedBanks = new ArrayList<>();

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

    public SpecificBanksAccountPaymentAccount() {
        super(PaymentMethod.SPECIFIC_BANKS_ID);
    }
}
