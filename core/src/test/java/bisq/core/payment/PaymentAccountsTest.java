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

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;

import java.util.Collections;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PaymentAccountsTest {
    @Test
    public void testGetOldestPaymentAccountForOfferWhenNoValidAccounts() {
        PaymentAccounts accounts = new PaymentAccounts(Collections.emptySet(), mock(AccountAgeWitnessService.class));
        PaymentAccount actual = accounts.getOldestPaymentAccountForOffer(mock(Offer.class));

        assertNull(actual);
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
