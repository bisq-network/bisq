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

import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;

import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SpecificBanksAccount.class, SameBankAccount.class, NationalBankAccount.class,
        MoneyGramAccount.class, WesternUnionAccount.class, CashDepositAccount.class, PaymentMethod.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
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
    public void testIsValidWhenSepaOffer() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(true);

        assertTrue(validator.isValid());
        verify(predicates).isMatchingSepaOffer(offer, account);
    }

    @Test
    public void testIsValidWhenSepaInstant() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(true);

        assertTrue(validator.isValid());
        verify(predicates).isMatchingSepaOffer(offer, account);
    }

    @Test
    public void testIsValidWhenSpecificBankAccountAndOfferRequireSpecificBank() {
        account = mock(SpecificBanksAccount.class);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
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
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
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
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
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
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
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
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
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
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(false);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }

    @Test
    public void testIsValidWhenWesternIrregularAccount() {
        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(true);
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(false);

        assertTrue(validator.isValid());
    }

    @Test
    public void testIsValidWhenMoneyGramAccount() {
        account = mock(MoneyGramAccount.class);

        PaymentMethod.MONEY_GRAM = mock(PaymentMethod.class);

        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.MONEY_GRAM);

        when(predicates.isMatchingCurrency(offer, account)).thenReturn(true);
        when(predicates.isEqualPaymentMethods(offer, account)).thenReturn(true);
        when(predicates.isMatchingCountryCodes(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaOffer(offer, account)).thenReturn(false);
        when(predicates.isMatchingSepaInstant(offer, account)).thenReturn(false);
        when(predicates.isOfferRequireSameOrSpecificBank(offer, account)).thenReturn(false);

        assertTrue(new ReceiptValidator(offer, account, predicates).isValid());
    }
}
