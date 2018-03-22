package io.bisq.api.model.payment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.bisq.api.model.payment.*;
import io.bisq.core.payment.payload.PaymentMethod;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "paymentMethod", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AliPayPaymentAccount.class, name = PaymentMethod.ALI_PAY_ID),
        @JsonSubTypes.Type(value = CashAppPaymentAccount.class, name = PaymentMethod.CASH_APP_ID),
        @JsonSubTypes.Type(value = CashDepositPaymentAccount.class, name = PaymentMethod.CASH_DEPOSIT_ID),
        @JsonSubTypes.Type(value = ChaseQuickPayPaymentAccount.class, name = PaymentMethod.CHASE_QUICK_PAY_ID),
        @JsonSubTypes.Type(value = ClearXchangePaymentAccount.class, name = PaymentMethod.CLEAR_X_CHANGE_ID),
        @JsonSubTypes.Type(value = FasterPaymentsPaymentAccount.class, name = PaymentMethod.FASTER_PAYMENTS_ID),
        @JsonSubTypes.Type(value = InteracETransferPaymentAccount.class, name = PaymentMethod.INTERAC_E_TRANSFER_ID),
        @JsonSubTypes.Type(value = MoneyBeamPaymentAccount.class, name = PaymentMethod.MONEY_BEAM_ID),
        @JsonSubTypes.Type(value = NationalBankAccountPaymentAccount.class, name = PaymentMethod.NATIONAL_BANK_ID),
        @JsonSubTypes.Type(value = OKPayPaymentAccount.class, name = PaymentMethod.OK_PAY_ID),
        @JsonSubTypes.Type(value = PerfectMoneyPaymentAccount.class, name = PaymentMethod.PERFECT_MONEY_ID),
        @JsonSubTypes.Type(value = PopmoneyPaymentAccount.class, name = PaymentMethod.POPMONEY_ID),
        @JsonSubTypes.Type(value = RevolutPaymentAccount.class, name = PaymentMethod.REVOLUT_ID),
        @JsonSubTypes.Type(value = SameBankAccountPaymentAccount.class, name = PaymentMethod.SAME_BANK_ID),
        @JsonSubTypes.Type(value = SepaPaymentAccount.class, name = PaymentMethod.SEPA_ID),
        @JsonSubTypes.Type(value = SepaInstantPaymentAccount.class, name = PaymentMethod.SEPA_INSTANT_ID),
        @JsonSubTypes.Type(value = SpecificBanksAccountPaymentAccount.class, name = PaymentMethod.SPECIFIC_BANKS_ID),
        @JsonSubTypes.Type(value = SwishPaymentAccount.class, name = PaymentMethod.SWISH_ID),
        @JsonSubTypes.Type(value = UpholdPaymentAccount.class, name = PaymentMethod.UPHOLD_ID),
        @JsonSubTypes.Type(value = USPostalMoneyOrderPaymentAccount.class, name = PaymentMethod.US_POSTAL_MONEY_ORDER_ID),
        @JsonSubTypes.Type(value = VenmoPaymentAccount.class, name = PaymentMethod.VENMO_ID),
        @JsonSubTypes.Type(value = WesternUnionPaymentAccount.class, name = PaymentMethod.WESTERN_UNION_ID)
})
public abstract class PaymentAccount {

    public String id;

    @NotBlank
    public String accountName;

    @NotBlank
    public String paymentMethod;

    @NotBlank
    public String selectedTradeCurrency;

    @NotEmpty
    public List<String> tradeCurrencies = new ArrayList<>();

    public PaymentAccount(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
