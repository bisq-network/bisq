package io.bisq.gui.main.offer.offerbook;

import io.bisq.locale.Country;
import io.bisq.locale.CryptoCurrency;
import io.bisq.locale.FiatCurrency;
import io.bisq.offer.Offer;
import io.bisq.payload.payment.*;
import io.bisq.payload.trade.offer.OfferPayload;
import io.bisq.payment.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferBookViewModelTest {
    private static final Logger log = LoggerFactory.getLogger(OfferBookViewModelTest.class);

    @Test
    public void testIsAnyPaymentAccountValidForOffer() {
        Offer offer;
        Collection<PaymentAccount> paymentAccounts;

        paymentAccounts = new ArrayList<>(Arrays.asList(getSepaAccount("EUR", "DE", "1212324", new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        // empty paymentAccounts
        paymentAccounts = new ArrayList<>();
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // simple cases: same payment methods

        // offer: okpay paymentAccount: okpay - same country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getOKPayAccount("EUR")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getOKPayPaymentMethod("EUR"), paymentAccounts));

        // offer: ether paymentAccount: ether - same country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getCryptoAccount("ETH")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getBlockChainsPaymentMethod("ETH"), paymentAccounts));

        // offer: sepa paymentAccount: sepa - same country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getSepaAccount("EUR", "AT", "1212324", new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // offer: nationalBank paymentAccount: nationalBank - same country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getNationalBankAccount("EUR", "AT", "PSK")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // offer: SameBank paymentAccount: SameBank - same country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getSameBankAccount("EUR", "AT", "PSK")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSameBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // offer: sepa paymentAccount: sepa - diff. country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getSepaAccount("EUR", "DE", "1212324", new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        //////

        // offer: sepa paymentAccount: sepa - same country, same currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getSepaAccount("EUR", "AT", "1212324", new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        // offer: sepa paymentAccount: nationalBank - same country, same currency
        // wrong method
        paymentAccounts = new ArrayList<>(Arrays.asList(getNationalBankAccount("EUR", "AT", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // wrong currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getNationalBankAccount("USD", "US", "XXX")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // wrong country
        paymentAccounts = new ArrayList<>(Arrays.asList(getNationalBankAccount("EUR", "FR", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // sepa wrong country
        paymentAccounts = new ArrayList<>(Arrays.asList(getNationalBankAccount("EUR", "CH", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // sepa wrong currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getNationalBankAccount("CHF", "DE", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        // same bank 
        paymentAccounts = new ArrayList<>(Arrays.asList(getSameBankAccount("EUR", "AT", "PSK")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // not same bank
        paymentAccounts = new ArrayList<>(Arrays.asList(getSameBankAccount("EUR", "AT", "Raika")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // same bank, wrong country
        paymentAccounts = new ArrayList<>(Arrays.asList(getSameBankAccount("EUR", "DE", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // same bank, wrong currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getSameBankAccount("USD", "AT", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank
        paymentAccounts = new ArrayList<>(Arrays.asList(getSpecificBanksAccount("EUR", "AT", "PSK",
                new ArrayList<>(Arrays.asList("PSK", "Raika")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank, missing bank
        paymentAccounts = new ArrayList<>(Arrays.asList(getSpecificBanksAccount("EUR", "AT", "PSK",
                new ArrayList<>(Arrays.asList("Raika")))));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank, wrong country
        paymentAccounts = new ArrayList<>(Arrays.asList(getSpecificBanksAccount("EUR", "FR", "PSK",
                new ArrayList<>(Arrays.asList("PSK", "Raika")))));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank, wrong currency
        paymentAccounts = new ArrayList<>(Arrays.asList(getSpecificBanksAccount("USD", "AT", "PSK",
                new ArrayList<>(Arrays.asList("PSK", "Raika")))));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        //TODO add more tests

    }

    private PaymentAccount getOKPayAccount(String currencyCode) {
        PaymentAccount paymentAccount = new OKPayAccount();
        paymentAccount.setSelectedTradeCurrency(new FiatCurrency(currencyCode));
        return paymentAccount;
    }

    private PaymentAccount getCryptoAccount(String currencyCode) {
        PaymentAccount paymentAccount = new CryptoCurrencyAccount();
        paymentAccount.addCurrency(new CryptoCurrency(currencyCode, null));
        return paymentAccount;
    }

    private PaymentAccount getSepaAccount(String currencyCode, String countryCode, String bic, ArrayList<String> countryCodes) {
        CountryBasedPaymentAccount paymentAccount = new SepaAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((SepaAccountContractData) paymentAccount.getContractData()).setBic(bic);
        countryCodes.forEach(((SepaAccountContractData) paymentAccount.getContractData())::addAcceptedCountry);
        return paymentAccount;
    }

    private PaymentAccount getNationalBankAccount(String currencyCode, String countryCode, String bankId) {
        CountryBasedPaymentAccount paymentAccount = new NationalBankAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((NationalBankAccountContractData) paymentAccount.getContractData()).setBankId(bankId);
        return paymentAccount;
    }

    private PaymentAccount getSameBankAccount(String currencyCode, String countryCode, String bankId) {
        SameBankAccount paymentAccount = new SameBankAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((SameBankAccountContractData) paymentAccount.getContractData()).setBankId(bankId);
        return paymentAccount;
    }

    private PaymentAccount getSpecificBanksAccount(String currencyCode, String countryCode, String bankId, ArrayList<String> bankIds) {
        SpecificBanksAccount paymentAccount = new SpecificBanksAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((SpecificBanksAccountContractData) paymentAccount.getContractData()).setBankId(bankId);
        bankIds.forEach(((SpecificBanksAccountContractData) paymentAccount.getContractData())::addAcceptedBank);
        return paymentAccount;
    }


    private Offer getBlockChainsPaymentMethod(String currencyCode) {
        return getOffer(currencyCode,
                PaymentMethod.BLOCK_CHAINS_ID,
                null,
                null,
                null,
                null);
    }

    private Offer getOKPayPaymentMethod(String currencyCode) {
        return getOffer(currencyCode,
                PaymentMethod.OK_PAY_ID,
                null,
                null,
                null,
                null);
    }

    private Offer getSEPAPaymentMethod(String currencyCode, String countryCode, ArrayList<String> countryCodes, String bankId) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.SEPA_ID,
                countryCode,
                countryCodes,
                bankId,
                null);
    }

    private Offer getNationalBankPaymentMethod(String currencyCode, String countryCode, String bankId) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.NATIONAL_BANK_ID,
                countryCode,
                new ArrayList<>(Arrays.asList(countryCode)),
                bankId,
                null);
    }

    private Offer getSameBankPaymentMethod(String currencyCode, String countryCode, String bankId) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.SAME_BANK_ID,
                countryCode,
                new ArrayList<>(Arrays.asList(countryCode)),
                bankId,
                new ArrayList<>(Arrays.asList(bankId)));
    }

    private Offer getSpecificBanksPaymentMethod(String currencyCode, String countryCode, String bankId, ArrayList<String> bankIds) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.SPECIFIC_BANKS_ID,
                countryCode,
                new ArrayList<>(Arrays.asList(countryCode)),
                bankId,
                bankIds);
    }

    private Offer getPaymentMethod(String currencyCode, String paymentMethodId, String countryCode, ArrayList<String> countryCodes, String bankId, ArrayList<String> bankIds) {
        return getOffer(currencyCode,
                paymentMethodId,
                countryCode,
                countryCodes,
                bankId,
                bankIds);
    }


    private Offer getOffer(String tradeCurrencyCode, String paymentMethodId, String countryCode, ArrayList<String> acceptedCountryCodes, String bankId, ArrayList<String> acceptedBanks) {
        return new Offer( new OfferPayload(null,
                null,
                null,
                null,
                null,
                0,
                0,
                false,
                0,
                0,
                tradeCurrencyCode,
                null,
                paymentMethodId,
                null,
                null,
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                0,
                0,
                false,
                null,
                null));
    }
}
