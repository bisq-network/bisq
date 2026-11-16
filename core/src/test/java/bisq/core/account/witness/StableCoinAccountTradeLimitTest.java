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
package bisq.core.account.witness;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.ClearXchangeAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountFactory;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.Preferences;

import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// A stablecoin payment method takes the fiat trade path, where the trade limit of a buyer depends on the age and the
// signing of the account for payment methods with chargeback risk. A transfer on a blockchain has no such risk.
public class StableCoinAccountTradeLimitTest {
    private AccountAgeWitnessService service;

    @BeforeEach
    public void setUp() {
        KeyRing keyRing = mock(KeyRing.class);
        PubKeyRing pubKeyRing = mock(PubKeyRing.class);
        when(pubKeyRing.getSignaturePubKeyBytes()).thenReturn(new byte[]{1, 2, 3});
        when(keyRing.getPubKeyRing()).thenReturn(pubKeyRing);
        Preferences preferences = mock(Preferences.class);
        when(preferences.getUserDefinedTradeLimit()).thenReturn(Long.MAX_VALUE);
        service = new AccountAgeWitnessService(keyRing,
                null,
                null,
                mock(SignedWitnessService.class),
                mock(ChargeBackRisk.class),
                null,
                mock(AppendOnlyDataStoreService.class),
                Clock.systemUTC(),
                preferences,
                mock(FilterPolicyService.class));
    }

    @Test
    public void newStableCoinAccountHasTheFullTradeLimitInBothDirections() {
        PaymentMethod paymentMethod = PaymentMethod.getPaymentMethod("USDT_TRC20");
        PaymentAccount account = PaymentAccountFactory.getPaymentAccount(paymentMethod);
        account.init();
        long maxTradeLimit = paymentMethod.getMaxTradeLimitAsCoin("USD").value;
        assertTrue(maxTradeLimit > OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value);

        assertEquals(maxTradeLimit, service.getMyTradeLimit(account, "USD", OfferDirection.BUY));
        assertEquals(maxTradeLimit, service.getMyTradeLimit(account, "USD", OfferDirection.SELL));
    }

    // Shows that the limit by account age applies in this setup, so the test above does not pass for another reason
    @Test
    public void newAccountOfAPaymentMethodWithChargebackRiskIsLimitedForBuying() {
        PaymentAccount account = new ClearXchangeAccount();
        account.init();

        assertEquals(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value,
                service.getMyTradeLimit(account, "USD", OfferDirection.BUY));
    }
}
