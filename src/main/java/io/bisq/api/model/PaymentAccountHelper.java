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
        converters.put(PaymentMethod.REVOLUT_ID, new RevolutPaymentAccountConverter());
        converters.put(PaymentMethod.SEPA_ID, new SepaPaymentAccountConverter());
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
