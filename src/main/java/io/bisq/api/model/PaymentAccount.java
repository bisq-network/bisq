package io.bisq.api.model;

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
        @JsonSubTypes.Type(value = RevolutPaymentAccount.class, name = PaymentMethod.REVOLUT_ID),
        @JsonSubTypes.Type(value = SepaPaymentAccount.class, name = PaymentMethod.SEPA_ID)
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
