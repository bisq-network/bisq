package bisq.httpapi.facade;

import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.validation.InputValidator;

import bisq.httpapi.exceptions.NotFoundException;
import bisq.httpapi.model.PaymentAccountList;
import bisq.httpapi.model.payment.PaymentAccountHelper;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;



import javax.validation.ValidationException;

public class PaymentAccountFacade {

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final User user;
    private final Preferences preferences;
    private final AltCoinAddressValidator altCoinAddressValidator;

    @Inject
    public PaymentAccountFacade(AccountAgeWitnessService accountAgeWitnessService,
                                User user,
                                bisq.core.user.Preferences preferences,
                                AltCoinAddressValidator altCoinAddressValidator) {

        this.accountAgeWitnessService = accountAgeWitnessService;
        this.user = user;
        this.preferences = preferences;
        this.altCoinAddressValidator = altCoinAddressValidator;
    }

    public PaymentAccount addPaymentAccount(PaymentAccount paymentAccount) {
        if (paymentAccount instanceof CryptoCurrencyAccount) {
            final CryptoCurrencyAccount cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
            final TradeCurrency tradeCurrency = cryptoCurrencyAccount.getSingleTradeCurrency();
            if (null == tradeCurrency) {
                throw new ValidationException("There must be exactly one trade currency");
            }
            altCoinAddressValidator.setCurrencyCode(tradeCurrency.getCode());
            final InputValidator.ValidationResult validationResult = altCoinAddressValidator.validate(cryptoCurrencyAccount.getAddress());
            if (!validationResult.isValid) {
                throw new ValidationException(validationResult.errorMessage);
            }
        }
        user.addPaymentAccount(paymentAccount);
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
        if (singleTradeCurrency != null) {
            if (singleTradeCurrency instanceof FiatCurrency)
                preferences.addFiatCurrency((FiatCurrency) singleTradeCurrency);
            else
                preferences.addCryptoCurrency((CryptoCurrency) singleTradeCurrency);
        } else if (tradeCurrencies != null && !tradeCurrencies.isEmpty()) {
            if (tradeCurrencies.contains(CurrencyUtil.getDefaultTradeCurrency()))
                paymentAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
            else
                paymentAccount.setSelectedTradeCurrency(tradeCurrencies.get(0));

            tradeCurrencies.forEach(tradeCurrency -> {
                if (tradeCurrency instanceof FiatCurrency)
                    preferences.addFiatCurrency((FiatCurrency) tradeCurrency);
                else
                    preferences.addCryptoCurrency((CryptoCurrency) tradeCurrency);
            });
        }

        accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        return paymentAccount;
    }

    public void removePaymentAccount(String id) {
        final PaymentAccount paymentAccount = user.getPaymentAccount(id);
        if (null == paymentAccount) {
            throw new NotFoundException("Payment account not found: " + id);
        }
        user.removePaymentAccount(paymentAccount);
    }

    private List<PaymentAccount> getPaymentAccountList() {
        return new ArrayList<>(user.getPaymentAccounts());
    }

    public PaymentAccountList getAccountList() {
        PaymentAccountList paymentAccountList = new PaymentAccountList();
        paymentAccountList.paymentAccounts = getPaymentAccountList().stream()
                .map(PaymentAccountHelper::toRestModel)
                .collect(Collectors.toList());
        return paymentAccountList;
    }
}
