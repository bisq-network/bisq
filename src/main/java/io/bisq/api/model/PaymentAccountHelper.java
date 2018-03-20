package io.bisq.api.model;

import io.bisq.common.locale.Country;
import io.bisq.common.locale.CountryUtil;
import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.payment.AliPayAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.RevolutAccount;
import io.bisq.core.payment.SepaAccount;
import io.bisq.core.payment.payload.PaymentMethod;

import javax.ws.rs.WebApplicationException;
import java.util.List;

public final class PaymentAccountHelper {

    public static PaymentAccount toBusinessModel(io.bisq.api.model.PaymentAccount rest) {
        if (PaymentMethod.ALI_PAY_ID.equals(rest.paymentMethod))
            return toBusinessModel((AliPayPaymentAccount) rest);
        if (PaymentMethod.REVOLUT_ID.equals(rest.paymentMethod))
            return toBusinessModel((RevolutPaymentAccount) rest);
        if (PaymentMethod.SEPA_ID.equals(rest.paymentMethod))
            return toBusinessModel((SepaPaymentAccount) rest);
        throw new WebApplicationException("Unsupported paymentMethod:" + rest.paymentMethod, 400);
    }

    public static AliPayAccount toBusinessModel(AliPayPaymentAccount rest) {
        final AliPayAccount business = new AliPayAccount();
        business.init();
        business.setAccountNr(rest.accountNr);
        toBusinessModel(business, rest);
        return business;
    }

    public static RevolutAccount toBusinessModel(RevolutPaymentAccount rest) {
        final RevolutAccount business = new RevolutAccount();
        business.init();
        business.setAccountId(rest.accountId);
        toBusinessModel(business, rest);
        return business;
    }

    public static SepaAccount toBusinessModel(SepaPaymentAccount rest) {
        final SepaAccount business = new SepaAccount();
        business.init();
        business.setBic(rest.bic);
        business.setIban(rest.iban);
        business.setHolderName(rest.holderName);
        business.setCountry(CountryUtil.findCountryByCode(rest.countryCode).get());
        toBusinessModel(business, rest);
        return business;
    }

    public static io.bisq.api.model.PaymentAccount toRestModel(PaymentAccount business) {
        if (business instanceof AliPayAccount)
            return toRestModel((AliPayAccount) business);
        if (business instanceof SepaAccount)
            return toRestModel((SepaAccount) business);
        throw new IllegalArgumentException("Unsupported payment account type:" + business.getPaymentMethod());
    }

    public static AliPayPaymentAccount toRestModel(AliPayAccount business) {
        final AliPayPaymentAccount rest = new AliPayPaymentAccount();
        rest.accountNr = business.getAccountNr();
        toRestModel(rest, business);
        return rest;
    }

    public static SepaPaymentAccount toRestModel(SepaAccount business) {
        final SepaPaymentAccount rest = new SepaPaymentAccount();
        rest.iban = business.getIban();
        rest.bic = business.getBic();
        final Country country = business.getCountry();
        if (null != country)
            rest.countryCode = country.code;
        rest.holderName = business.getHolderName();
        toRestModel(rest, business);
        return rest;
    }

    private static void toBusinessModel(PaymentAccount business, io.bisq.api.model.PaymentAccount rest) {
        if (null != rest.accountName)
            business.setAccountName(rest.accountName);
        if (null != rest.selectedTradeCurrency)
            business.setSelectedTradeCurrency(new FiatCurrency(rest.selectedTradeCurrency));
        if (null != rest.tradeCurrencies) {
            rest.tradeCurrencies.stream().forEach(currencyCode -> {
                business.addCurrency(new FiatCurrency(currencyCode));
            });
        }
    }

    private static void toRestModel(io.bisq.api.model.PaymentAccount rest, PaymentAccount business) {
        rest.id = business.getId();
        rest.accountName = business.getAccountName();
        final TradeCurrency selectedTradeCurrency = business.getSelectedTradeCurrency();
        if (null != selectedTradeCurrency)
            rest.selectedTradeCurrency = selectedTradeCurrency.getCode();
        final List<TradeCurrency> tradeCurrencies = business.getTradeCurrencies();
        if (null != tradeCurrencies)
            tradeCurrencies.stream().forEach(currency -> rest.tradeCurrencies.add(currency.getCode()));
    }
}
