package io.bisq.core.payment;

import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentMethod;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SpecificBanksAccount.class, SameBankAccount.class, NationalBankAccount.class,
        WesternUnionAccount.class, CashDepositAccount.class, PaymentMethod.class})
public class ReceiptValidatorTest {
    private ReceiptValidator validator;
    private PaymentAccount account;
    private Offer offer;
    private ReceiptPredicates predicates;

    @Before
    public void setUp() {
        this.predicates = mock(ReceiptPredicates.class);
        this.account = mock(CountryBasedPaymentAccount.class);
        this.offer = mock(Offer.class);
        this.validator = new ReceiptValidator(offer, account, predicates);
    }

    @Test
    public void testIsValidWhenCurrencyDoesNotMatch() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(false);

        assertFalse(validator.isValid());
        verify(predicates).isMatchingCurrency(offer, account);
    }

    @Test
    public void testIsValidWhenNotCountryBasedAccount() {
        account = mock(PaymentAccount.class);
        assertFalse(account instanceof CountryBasedPaymentAccount);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenNotMatchingCodes() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(false);

        assertFalse(validator.isValid());
        verify(predicates).isMatchingCountryCodes(offer, account);
    }

    @Test
    public void testIsValidWhenSepaRelated() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(false);
        when(predicates.isSepaRelated(offer, account)).thenReturn(true);

        assertTrue(validator.isValid());
        verify(predicates).isSepaRelated(offer, account);
    }

    @Test
    public void testIsValidWhenSpecificBankAccountAndOfferRequireSpecificBank() {
        account = mock(SpecificBanksAccount.class);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(true);
        when(predicates.isMatchingBankId(offer, account)).thenReturn(false);

        assertFalse(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenSameBankAccountAndOfferRequireSpecificBank() {
        account = mock(SameBankAccount.class);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(true);
        when(predicates.isMatchingBankId(offer, account)).thenReturn(false);

        assertFalse(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenSpecificBankAccount() {
        account = mock(SpecificBanksAccount.class);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(true);
        when(predicates.isMatchingBankId(offer, account)).thenReturn(true);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenSameBankAccount() {
        account = mock(SameBankAccount.class);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(true);
        when(predicates.isMatchingBankId(offer, account)).thenReturn(true);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenNationalBankAccount() {
        account = mock(NationalBankAccount.class);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(false);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenWesternUnionAccount() {
        account = mock(WesternUnionAccount.class);

        PaymentMethod.WESTERN_UNION = mock(PaymentMethod.class);

        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.WESTERN_UNION);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(false);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenWesternIrregularAccount() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isSepaRelated(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(false);

        assertTrue(validator.isValid());
    }
}
