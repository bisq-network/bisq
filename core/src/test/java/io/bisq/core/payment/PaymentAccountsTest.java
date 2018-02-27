package io.bisq.core.payment;

import com.google.common.collect.Sets;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PaymentAccount.class, AccountAgeWitness.class})
public class PaymentAccountsTest {
    @Test
    public void testGetOldestPaymentAccountForOfferWhenNoValidAccounts() {
        PaymentAccounts accounts = new PaymentAccounts(Collections.emptySet(), mock(AccountAgeWitnessService.class));
        PaymentAccount actual = accounts.getOldestPaymentAccountForOffer(mock(Offer.class));

        assertNull(actual);
    }

    @Test
    public void testGetOldestPaymentAccountForOffer() {
        AccountAgeWitnessService service = mock(AccountAgeWitnessService.class);

        PaymentAccount oldest = createAccountWithAge(service, 3);
        Set<PaymentAccount> accounts = Sets.newHashSet(
                oldest,
                createAccountWithAge(service, 2),
                createAccountWithAge(service, 1));

        BiFunction<Offer, PaymentAccount, Boolean> dummyValidator = (offer, account) -> true;
        PaymentAccounts testedEntity = new PaymentAccounts(accounts, service, dummyValidator);

        PaymentAccount actual = testedEntity.getOldestPaymentAccountForOffer(mock(Offer.class));
        assertEquals(oldest, actual);
    }

    private static PaymentAccount createAccountWithAge(AccountAgeWitnessService service, long age) {
        PaymentAccountPayload payload = mock(PaymentAccountPayload.class);

        PaymentAccount account = mock(PaymentAccount.class);
        when(account.getPaymentAccountPayload()).thenReturn(payload);

        AccountAgeWitness witness = mock(AccountAgeWitness.class);
        when(service.getAccountAge(eq(witness), any())).thenReturn(age);

        when(service.getMyWitness(payload)).thenReturn(witness);

        return account;
    }
}
