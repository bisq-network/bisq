package bisq.core.payment;

import bisq.core.exceptions.ValidationException;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.validation.InputValidator;

import com.google.inject.Inject;

import java.util.List;

public class PaymentAccountManager {

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final Preferences preferences;
    private final User user;
    private AltCoinAddressValidator altCoinAddressValidator;

    @Inject
    public PaymentAccountManager(AccountAgeWitnessService accountAgeWitnessService, AltCoinAddressValidator altCoinAddressValidator, Preferences preferences, User user) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.preferences = preferences;
        this.user = user;
    }

    public PaymentAccount addPaymentAccount(PaymentAccount paymentAccount) {
        if (paymentAccount instanceof CryptoCurrencyAccount) {
            CryptoCurrencyAccount cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
            TradeCurrency tradeCurrency = cryptoCurrencyAccount.getSingleTradeCurrency();
            if (tradeCurrency == null) {
                throw new ValidationException("CryptoCurrency account must have exactly one trade currency");
            }
            altCoinAddressValidator.setCurrencyCode(tradeCurrency.getCode());
            InputValidator.ValidationResult validationResult = altCoinAddressValidator.validate(cryptoCurrencyAccount.getAddress());
            if (!validationResult.isValid) {
                throw new ValidationException(validationResult.errorMessage);
            }
        }
//        TODO we should validate payment account here as well
        user.addPaymentAccount(paymentAccount);
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
        if (singleTradeCurrency != null) {
            if (singleTradeCurrency instanceof FiatCurrency)
                preferences.addFiatCurrency((FiatCurrency) singleTradeCurrency);
            else
                preferences.addCryptoCurrency((CryptoCurrency) singleTradeCurrency);
        } else if (!tradeCurrencies.isEmpty()) {
            tradeCurrencies.forEach(tradeCurrency -> {
                if (tradeCurrency instanceof FiatCurrency)
                    preferences.addFiatCurrency((FiatCurrency) tradeCurrency);
                else
                    preferences.addCryptoCurrency((CryptoCurrency) tradeCurrency);
            });
        }
        if (!(paymentAccount instanceof CryptoCurrencyAccount)) {
            if (singleTradeCurrency == null && !tradeCurrencies.isEmpty()) {
                if (tradeCurrencies.contains(CurrencyUtil.getDefaultTradeCurrency()))
                    paymentAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
                else
                    paymentAccount.setSelectedTradeCurrency(tradeCurrencies.get(0));

            }
            accountAgeWitnessService.publishMyAccountAgeWitness(paymentAccount.getPaymentAccountPayload());
        }
        return paymentAccount;
    }
}
