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

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.payment.payload.PaymentMethod;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TradeLimitsTest {
    @Test
    public void testGetFirstMonthRiskBasedTradeLimit() {
        TradeLimits tradeLimits = new TradeLimits(mock(DaoStateService.class), mock(PeriodService.class));
        long expected, result;

        expected = 0;
        result = tradeLimits.getFirstMonthRiskBasedTradeLimit(0, 1);
        assertEquals(expected, result);

        expected = 25000000;
        result = tradeLimits.getFirstMonthRiskBasedTradeLimit(100000000, 1);
        assertEquals(expected, result);

        expected = 3130000; //0.03125 -> 0.0313 -> 0.0313
        result = tradeLimits.getFirstMonthRiskBasedTradeLimit(100000000, 8);
        assertEquals(expected, result);

        expected = 6250000;
        result = tradeLimits.getFirstMonthRiskBasedTradeLimit(200000000, 8);
        assertEquals(expected, result);

        expected = 25000000;
        result = tradeLimits.getRoundedRiskBasedTradeLimit(25000000, 1);
        assertEquals(expected, result);

        expected = 12520000;
        result = tradeLimits.getRoundedRiskBasedTradeLimit(25000000, 2);
        assertEquals(expected, result);

        expected = 8320000;
        result = tradeLimits.getRoundedRiskBasedTradeLimit(25000000, 3);
        assertEquals(expected, result);

        expected = 6240000;
        result = tradeLimits.getRoundedRiskBasedTradeLimit(25000000, 4);
        assertEquals(expected, result);
    }

    @Test
    public void testRiskCategoryMaxTradeLimitsWithCurrentCap() {
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        when(periodService.getChainHeight()).thenReturn(0);
        when(daoStateService.getParamValueAsCoin(Param.MAX_TRADE_LIMIT, 0)).thenReturn(Coin.parseCoin("2"));

        new TradeLimits(daoStateService, periodService);

        assertEquals(Coin.parseCoin("0.250"), getMaxTradeLimit(PaymentMethod.ADVANCED_CASH_ID, "USD"));
        assertEquals(Coin.parseCoin("0.1252"), getMaxTradeLimit(PaymentMethod.SWISH_ID, "SEK"));
        assertEquals(Coin.parseCoin("0.0832"), getMaxTradeLimit(PaymentMethod.SWIFT_ID, "USD"));
        assertEquals(Coin.parseCoin("0.0624"), getMaxTradeLimit(PaymentMethod.SEPA_ID, "EUR"));
    }

    private Coin getMaxTradeLimit(String paymentMethodId, String currencyCode) {
        return PaymentMethod.getPaymentMethod(paymentMethodId).getMaxTradeLimitAsCoin(currencyCode);
    }
}
