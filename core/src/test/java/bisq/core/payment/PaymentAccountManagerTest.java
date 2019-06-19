package bisq.core.payment;

import bisq.core.exceptions.ValidationException;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.validation.InputValidator;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PaymentAccountManagerTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private AccountAgeWitnessService accountAgeWitnessService;
    private AltCoinAddressValidator altCoinAddressValidator;
    private PaymentAccountManager paymentAccountManager;
    private Preferences preferences;
    private User user;
    private TradeCurrency globalDefaultTradeCurrency;
    private FiatCurrency defaultTradeCurrency;

    @Before
    public void setUp() {
        accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        altCoinAddressValidator = mock(AltCoinAddressValidator.class);
        preferences = mock(Preferences.class);
        user = mock(User.class);
        paymentAccountManager = new PaymentAccountManager(accountAgeWitnessService, altCoinAddressValidator, preferences, user);
        globalDefaultTradeCurrency = GlobalSettings.getDefaultTradeCurrency();
        defaultTradeCurrency = new FiatCurrency("CAD");
        GlobalSettings.setDefaultTradeCurrency(defaultTradeCurrency);
    }

    @After
    public void tearDown() {
        GlobalSettings.setDefaultTradeCurrency(globalDefaultTradeCurrency);
    }

    @Test
    public void addPaymentAccount_cryptoAccountMissingSingleTradeCurrency_throwsValidationException() {
        //        Given
        CryptoCurrencyAccount account = new CryptoCurrencyAccount();
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage("CryptoCurrency account must have exactly one trade currency");

        //        When
        paymentAccountManager.addPaymentAccount(account);
    }

    @Test
    public void addPaymentAccount_invalidAddressForCryptoAccount_throwsValidationException() {
        //        Given
        CryptoCurrencyAccount account = new CryptoCurrencyAccount();
        account.init();
        account.setSingleTradeCurrency(new CryptoCurrency("ABCDEF", "Abc"));
        String validationErrorMessage = UUID.randomUUID().toString();
        String address = UUID.randomUUID().toString();
        account.setAddress(address);
        InputValidator.ValidationResult validationResult = new InputValidator.ValidationResult(false, validationErrorMessage);
        when(altCoinAddressValidator.validate(address)).thenReturn(validationResult);
        expectedException.expect(ValidationException.class);
        expectedException.expectMessage(validationErrorMessage);

        //        When
        paymentAccountManager.addPaymentAccount(account);
    }

    @Ignore("Not implemented yet")
    @Test
    public void addPaymentAccount_validationFailsForFiatAccount_throwsValidationException() {
        //        Given

        //        When

        //        Then
        fail("Not implemented yet");
    }

    @Test
    public void addPaymentAccount_validationSuccess_callsAddPaymentAccountOnTheUser() {
        //        Given
        AliPayAccount account = new AliPayAccount();
        account.init();
        String address = UUID.randomUUID().toString();
        account.setAccountNr(address);

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        verify(user).addPaymentAccount(account);
    }

    @Test
    public void addPaymentAccount_validationSuccess_returnsSamePaymentAccount() {
        //        Given
        CashDepositAccount account = new CashDepositAccount();
        account.init();
        String requirements = UUID.randomUUID().toString();
        account.setRequirements(requirements);

        //        When
        PaymentAccount result = paymentAccountManager.addPaymentAccount(account);

        //        Then
        assertSame(account, result);
    }

    @Test
    public void addPaymentAccount_singleTradeCurrencyIsFiat_addsCurrencyToPreferences() {
        //        Given
        CashDepositAccount account = new CashDepositAccount();
        account.init();
        FiatCurrency tradeCurrency = new FiatCurrency("USD");
        account.setSingleTradeCurrency(tradeCurrency);

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        verify(preferences).addFiatCurrency(tradeCurrency);
    }

    @Test
    public void addPaymentAccount_singleTradeCurrencyIsCrypto_addsCurrencyToPreferences() {
        //        Given
        CashDepositAccount account = new CashDepositAccount();
        account.init();
        CryptoCurrency tradeCurrency = new CryptoCurrency("XBT", "Bitcoin");
        account.setSingleTradeCurrency(tradeCurrency);

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        verify(preferences).addCryptoCurrency(tradeCurrency);
    }

    @Test
    public void addPaymentAccount_oneFiatAndOneCryptoTradeCurrency_addsCurrenciesToPreferences() {
        //        Given
        CashDepositAccount account = new CashDepositAccount();
        account.init();
        FiatCurrency fiatCurrency = new FiatCurrency("GBP");
        CryptoCurrency cryptoCurrency = new CryptoCurrency("XMR", "Monero");
        account.getTradeCurrencies().add(fiatCurrency);
        account.getTradeCurrencies().add(cryptoCurrency);

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        verify(preferences).addCryptoCurrency(cryptoCurrency);
        verify(preferences).addFiatCurrency(fiatCurrency);
    }

    @Test
    public void addPaymentAccount_cryptoAccount_doesNotPublishAccountAgeWitness() {
        //        Given
        when(altCoinAddressValidator.validate(any())).thenReturn(new InputValidator.ValidationResult(true));
        CryptoCurrencyAccount account = new CryptoCurrencyAccount();
        account.init();
        account.setSingleTradeCurrency(new CryptoCurrency("XMR", "Monero"));
        account.setAddress(UUID.randomUUID().toString());
        PaymentAccountPayload paymentAccountPayload = account.getPaymentAccountPayload();

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        assertNotNull(paymentAccountPayload);
        verify(accountAgeWitnessService, never()).publishMyAccountAgeWitness(paymentAccountPayload);
    }

    @Test
    public void addPaymentAccount_fiatAccount_publishesAccountAgeWitness() {
        //        Given
        RevolutAccount account = new RevolutAccount();
        account.init();
        PaymentAccountPayload paymentAccountPayload = account.getPaymentAccountPayload();

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        assertNotNull(paymentAccountPayload);
        verify(accountAgeWitnessService).publishMyAccountAgeWitness(paymentAccountPayload);
    }

    @Test
    public void addPaymentAccount_fiatAccountHasDefaultTradeCurrencyAsSecondCurrency_setDefaultTradeCurrencyAsSelectedTradeCurrency() {
        //        Given
        RevolutAccount account = new RevolutAccount();
        account.init();
        account.getTradeCurrencies().clear();
        FiatCurrency aud = new FiatCurrency("AUD");
        account.getTradeCurrencies().add(aud);
        account.getTradeCurrencies().add(defaultTradeCurrency);

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        assertNotEquals(defaultTradeCurrency, aud);
        assertEquals(defaultTradeCurrency, account.getSelectedTradeCurrency());
    }

    @Test
    public void addPaymentAccount_multipleTradeCurrencies_setFirstOneAsSelectedTradeCurrency() {
        //        Given
        RevolutAccount account = new RevolutAccount();
        account.init();
        account.getTradeCurrencies().clear();
        FiatCurrency gbp = new FiatCurrency("GBP");
        FiatCurrency aud = new FiatCurrency("AUD");
        account.getTradeCurrencies().add(gbp);
        account.getTradeCurrencies().add(aud);

        //        When
        paymentAccountManager.addPaymentAccount(account);

        //        Then
        assertNotEquals(defaultTradeCurrency, aud);
        assertNotEquals(defaultTradeCurrency, gbp);
        assertEquals(gbp, account.getSelectedTradeCurrency());
    }
}
