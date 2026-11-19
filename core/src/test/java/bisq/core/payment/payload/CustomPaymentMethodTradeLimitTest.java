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

package bisq.core.payment.payload;

import bisq.core.payment.TradeLimits;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

// The trade limit of the custom payment method is that of the highest risk category, but at most 0.01 BTC.
// SEPA is in the highest risk category and shows the limit without the cap.
public class CustomPaymentMethodTradeLimitTest {
    private static final PaymentMethod CUSTOM = PaymentMethod.getPaymentMethod(PaymentMethod.CUSTOM_ID);
    private static final PaymentMethod SEPA = PaymentMethod.getPaymentMethod(PaymentMethod.SEPA_ID);

    @Test
    public void customPaymentMethodIsInTheHighestRiskCategory() {
        assertEquals(SEPA.toProtoMessage().getMaxTradeLimit(), CUSTOM.toProtoMessage().getMaxTradeLimit());
        assertEquals(Coin.parseCoin("0.01"), PaymentMethod.CUSTOM_MAX_TRADE_LIMIT);
    }

    // The DAO param of 2 BTC is capped to 0.25 BTC at the time of writing
    @Test
    public void limitIsCappedBelowTheLimitOfTheHighestRiskCategory() {
        TradeLimits tradeLimits = tradeLimits("0.25");

        assertEquals(Coin.parseCoin("0.0624"), SEPA.getMaxTradeLimitAsCoin(tradeLimits));
        assertEquals(Coin.parseCoin("0.01"), CUSTOM.getMaxTradeLimitAsCoin(tradeLimits));
    }

    @Test
    public void limitDoesNotGrowWithTheMaxTradeLimit() {
        TradeLimits tradeLimits = tradeLimits("0.5");

        assertEquals(Coin.parseCoin("0.1252"), SEPA.getMaxTradeLimitAsCoin(tradeLimits));
        assertEquals(Coin.parseCoin("0.01"), CUSTOM.getMaxTradeLimitAsCoin(tradeLimits));
    }

    @Test
    public void limitFollowsALowerLimitOfTheHighestRiskCategory() {
        TradeLimits tradeLimits = tradeLimits("0.03");

        assertEquals(Coin.parseCoin("0.0076"), SEPA.getMaxTradeLimitAsCoin(tradeLimits));
        assertEquals(Coin.parseCoin("0.0076"), CUSTOM.getMaxTradeLimitAsCoin(tradeLimits));
    }

    // Without the trade limits of the DAO every payment method gets the max trade amount
    @Test
    public void limitIsCappedWithoutTradeLimits() {
        assertEquals(TradeLimits.MAX_TRADE_AMOUNT, SEPA.getMaxTradeLimitAsCoin((TradeLimits) null));
        assertEquals(Coin.parseCoin("0.01"), CUSTOM.getMaxTradeLimitAsCoin((TradeLimits) null));
    }

    // A mock is not constructed, so it does not replace the static instance which other tests use
    private static TradeLimits tradeLimits(String maxTradeLimitFromDaoParam) {
        TradeLimits tradeLimits = mock(TradeLimits.class, CALLS_REAL_METHODS);
        doReturn(Coin.parseCoin(maxTradeLimitFromDaoParam)).when(tradeLimits).getMaxTradeLimitFromDaoParam();
        return tradeLimits;
    }
}
