/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.payment;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.exceptions.NotFoundException;
import bisq.core.exceptions.ValidationException;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.validation.InputValidator;

import javafx.collections.FXCollections;

import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static java.lang.String.format;
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
    private OpenOfferManager openOfferManager;
    private PaymentAccountManager paymentAccountManager;
    private Preferences preferences;
    private TradeManager tradeManager;
    private User user;
    private TradeCurrency globalDefaultTradeCurrency;
    private FiatCurrency defaultTradeCurrency;

    @Before
    public void setUp() {
        accountAgeWitnessService = mock(AccountAgeWitnessService.class);
        altCoinAddressValidator = mock(AltCoinAddressValidator.class);
        openOfferManager = mock(OpenOfferManager.class);
        preferences = mock(Preferences.class);
        tradeManager = mock(TradeManager.class);
        user = mock(User.class);
        paymentAccountManager = new PaymentAccountManager(accountAgeWitnessService, altCoinAddressValidator, openOfferManager, preferences, tradeManager, user);
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

    @Test
    public void removePaymentAccount_nullId_throwNotFoundException() {
//        Given
        expectedException.expect(NotFoundException.class);
        expectedException.expectMessage("Payment account null not found");
//        When
        paymentAccountManager.removePaymentAccount(null);
    }

    @Test
    public void removePaymentAccount_accountUsedInOpenOffer_throwsException() {
//        Given
        String id = getRandomString();
        when(user.getPaymentAccount(id)).thenReturn(mock(PaymentAccount.class));
        Offer offer = mock(Offer.class);
        when(offer.getMakerPaymentAccountId()).thenReturn(id);
        OpenOffer openOffer = mock(OpenOffer.class);
        when(openOffer.getOffer()).thenReturn(offer);
        when(openOfferManager.getObservableList()).thenReturn(FXCollections.observableArrayList(openOffer));
        expectedException.expect(PaymentAccountInUseException.class);
        expectedException.expectMessage(format("Payment account %s is used for open offer", id));

//        When
        paymentAccountManager.removePaymentAccount(id);
    }

    @Test
    public void removePaymentAccount_accountUsedInOpenTradeForMaker_throwsException() {
//        Given
        String id = getRandomString();
        when(user.getPaymentAccount(id)).thenReturn(mock(PaymentAccount.class));
        Offer offer = mock(Offer.class);
        when(offer.getMakerPaymentAccountId()).thenReturn(id);
        Trade trade = mock(Trade.class);
        when(trade.getOffer()).thenReturn(offer);
        when(tradeManager.getTradableList()).thenReturn(FXCollections.observableArrayList(trade));
        when(openOfferManager.getObservableList()).thenReturn(FXCollections.observableArrayList());
        expectedException.expect(PaymentAccountInUseException.class);
        expectedException.expectMessage(format("Payment account %s is used for open trade", id));

//        When
        paymentAccountManager.removePaymentAccount(id);
    }

    @Test
    public void removePaymentAccount_accountUsedInOpenTradeForTaker_throwsException() {
//        Given
        String id = getRandomString();
        when(user.getPaymentAccount(id)).thenReturn(mock(PaymentAccount.class));
        Trade trade = mock(Trade.class);
        when(trade.getTakerPaymentAccountId()).thenReturn(id);
        when(tradeManager.getTradableList()).thenReturn(FXCollections.observableArrayList(trade));
        when(openOfferManager.getObservableList()).thenReturn(FXCollections.observableArrayList());
        expectedException.expect(PaymentAccountInUseException.class);
        expectedException.expectMessage(format("Payment account %s is used for open trade", id));

//        When
        paymentAccountManager.removePaymentAccount(id);
    }

    @Test
    public void removePaymentAccount_accountExistAndIsNotUsed_removeAccount() {
//        Given
        String id = getRandomString();
        PaymentAccount paymentAccount = mock(PaymentAccount.class);
        when(user.getPaymentAccount(id)).thenReturn(paymentAccount);
        when(tradeManager.getTradableList()).thenReturn(FXCollections.observableArrayList());
        when(openOfferManager.getObservableList()).thenReturn(FXCollections.observableArrayList());

//        When
        paymentAccountManager.removePaymentAccount(id);

//        Then
        verify(user).removePaymentAccount(paymentAccount);
    }

    private String getRandomString() {
        return UUID.randomUUID().toString();
    }
}
