package io.bisq.api.model;

import io.bisq.common.locale.Country;
import io.bisq.common.locale.CountryUtil;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.SepaAccount;
import io.bisq.core.payment.payload.PaymentMethod;

import javax.ws.rs.WebApplicationException;
import java.util.List;

public final class PaymentAccountHelper {

    public static PaymentAccount toBusinessModel(io.bisq.api.model.PaymentAccount account) {
        if (PaymentMethod.SEPA_ID.equals(account.paymentMethod)) {
            return toBusinessModel((SepaPaymentAccount) account);
        }
        throw new WebApplicationException("Unsupported paymentMethod:" + account.paymentMethod, 400);
    }


    public static SepaAccount toBusinessModel(SepaPaymentAccount account) {
        final SepaAccount paymentAccount = new SepaAccount();
        paymentAccount.init();
        paymentAccount.setBic(account.bic);
        paymentAccount.setIban(account.iban);
        paymentAccount.setHolderName(account.holderName);
        paymentAccount.setAccountName(account.accountName);
        paymentAccount.setCountry(CountryUtil.findCountryByCode(account.countryCode).get());
        if (null != account.selectedTradeCurrency)
            paymentAccount.setSelectedTradeCurrency(new FiatCurrency(account.selectedTradeCurrency));
        if (null != account.tradeCurrencies) {
            account.tradeCurrencies.stream().forEach(currencyCode -> {
                paymentAccount.addCurrency(new FiatCurrency(currencyCode));
                paymentAccount.addAcceptedCountry(currencyCode);
            });
        }
        return paymentAccount;
    }

    public static io.bisq.api.model.PaymentAccount toRestModel(PaymentAccount account) {
        if (account instanceof SepaAccount) {
            return toRestModel((SepaAccount) account);
        }
        throw new IllegalArgumentException("Unsupported payment account type:" + account.getPaymentMethod());
    }

    public static SepaPaymentAccount toRestModel(SepaAccount account) {
        final SepaPaymentAccount sepaPaymentAccount = new SepaPaymentAccount();
        sepaPaymentAccount.id = account.getId();
        sepaPaymentAccount.accountName = account.getAccountName();
        sepaPaymentAccount.iban = account.getIban();
        sepaPaymentAccount.bic = account.getBic();
        final Country country = account.getCountry();
        if (null != country)
            sepaPaymentAccount.countryCode = country.code;
        sepaPaymentAccount.holderName = account.getHolderName();
        final TradeCurrency selectedTradeCurrency = account.getSelectedTradeCurrency();
        if (null != selectedTradeCurrency)
            sepaPaymentAccount.selectedTradeCurrency = selectedTradeCurrency.getCode();
        final List<TradeCurrency> tradeCurrencies = account.getTradeCurrencies();
        if (null != tradeCurrencies)
            tradeCurrencies.stream().forEach(currency -> sepaPaymentAccount.tradeCurrencies.add(currency.getCode()));
        return sepaPaymentAccount;
    }
}
