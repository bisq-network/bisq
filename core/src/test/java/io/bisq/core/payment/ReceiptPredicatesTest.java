package io.bisq.core.payment;

import com.google.common.collect.Lists;
import io.bisq.common.locale.CryptoCurrency;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({NationalBankAccount.class, SepaAccount.class, SepaInstantAccount.class, PaymentMethod.class, SameBankAccount.class, SpecificBanksAccount.class})
public class ReceiptPredicatesTest {
    private final ReceiptPredicates predicates = new ReceiptPredicates();

    @Test
    public void testIsMatchingCurrency() {
        Offer offer = mock(Offer.class);
        when(offer.getCurrencyCode()).thenReturn("USD");

        PaymentAccount account = mock(PaymentAccount.class);
        when(account.getTradeCurrencies()).thenReturn(Lists.newArrayList(
                new CryptoCurrency("BTC", "Bitcoin"),
                new CryptoCurrency("ETH", "Ether")));

        assertFalse(predicates.isMatchingCurrency(offer, account));
    }

    @Test
    public void testIsSepaRelated() {
        Offer offer = mock(Offer.class);
        PaymentMethod.SEPA = mock(PaymentMethod.class);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.SEPA);

        assertTrue(predicates.isSepaRelated(offer, mock(SepaInstantAccount.class)));
        assertTrue(predicates.isSepaRelated(offer, mock(SepaAccount.class)));

        PaymentMethod.SEPA_INSTANT = mock(PaymentMethod.class);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.SEPA_INSTANT);

        assertTrue(predicates.isSepaRelated(offer, mock(SepaInstantAccount.class)));
        assertTrue(predicates.isSepaRelated(offer, mock(SepaAccount.class)));
    }

    @Test
    public void testIsMatchingCountryCodes() {
        CountryBasedPaymentAccount account = mock(CountryBasedPaymentAccount.class);
        when(account.getCountry()).thenReturn(null);

        assertFalse(predicates.isMatchingCountryCodes(mock(Offer.class), account));
    }

    @Test
    public void testIsSameOrSpecificBank() {
        PaymentMethod.SAME_BANK = mock(PaymentMethod.class);

        Offer offer = mock(Offer.class);
        when(offer.getPaymentMethod()).thenReturn(PaymentMethod.SAME_BANK);

        assertTrue(predicates.isOfferRequireSameOrSpecificBank(offer, mock(NationalBankAccount.class)));
    }

    @Test
    public void testIsEqualPaymentMethods() {
        final PaymentMethod method = new PaymentMethod("1");

        Offer offer = mock(Offer.class);
        when(offer.getPaymentMethod()).thenReturn(method);

        PaymentAccount account = mock(PaymentAccount.class);
        when(account.getPaymentMethod()).thenReturn(method);

        assertTrue(predicates.isEqualPaymentMethods(offer, account));
    }
}
