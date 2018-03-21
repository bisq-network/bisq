package io.bisq.api.model;

import io.bisq.api.model.payment.*;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentMethod;

import javax.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.Map;

public final class PaymentAccountHelper {

    private static Map<String, PaymentAccountConverter> converters = new HashMap<>();

    static {
        converters.put(PaymentMethod.ALI_PAY_ID, new AliPayPaymentAccountConverter());
        converters.put(PaymentMethod.CASH_APP_ID, new CashAppPaymentAccountConverter());
        converters.put(PaymentMethod.CASH_DEPOSIT_ID, new CashDepositPaymentAccountConverter());
        converters.put(PaymentMethod.CHASE_QUICK_PAY_ID, new ChaseQuickPayPaymentAccountConverter());
        converters.put(PaymentMethod.CLEAR_X_CHANGE_ID, new ClearXchangePaymentAccountConverter());
        converters.put(PaymentMethod.FASTER_PAYMENTS_ID, new FasterPaymentsPaymentAccountConverter());
        converters.put(PaymentMethod.INTERAC_E_TRANSFER_ID, new InteracETransferPaymentAccountConverter());
        converters.put(PaymentMethod.MONEY_BEAM_ID, new MoneyBeamPaymentAccountConverter());
        converters.put(PaymentMethod.NATIONAL_BANK_ID, new NationalBankAccountPaymentAccountConverter());
        converters.put(PaymentMethod.OK_PAY_ID, new OKPayPaymentAccountConverter());
        converters.put(PaymentMethod.PERFECT_MONEY_ID, new PerfectMoneyPaymentAccountConverter());
        converters.put(PaymentMethod.POPMONEY_ID, new PopmoneyPaymentAccountConverter());
        converters.put(PaymentMethod.REVOLUT_ID, new RevolutPaymentAccountConverter());
        converters.put(PaymentMethod.SAME_BANK_ID, new SameBankAccountPaymentAccountConverter());
        converters.put(PaymentMethod.SEPA_ID, new SepaPaymentAccountConverter());
        converters.put(PaymentMethod.SEPA_INSTANT_ID, new SepaInstantPaymentAccountConverter());
    }

    public static PaymentAccount toBusinessModel(io.bisq.api.model.PaymentAccount rest) {
        final PaymentAccountConverter converter = converters.get(rest.paymentMethod);
        if (null != converter) {
            return converter.toBusinessModel(rest);
        }
        throw new WebApplicationException("Unsupported paymentMethod:" + rest.paymentMethod, 400);
    }

    public static io.bisq.api.model.PaymentAccount toRestModel(PaymentAccount business) {
        final PaymentMethod paymentMethod = business.getPaymentMethod();
        final PaymentAccountConverter converter = converters.get(paymentMethod.getId());
        if (null != converter) {
            return converter.toRestModel(business);
        }
        throw new IllegalArgumentException("Unsupported paymentMethod:" + paymentMethod);
    }

}
