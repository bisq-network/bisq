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

package bisq.core.trade.statistics;

import bisq.core.payment.payload.PaymentMethod;

import com.google.common.collect.Sets;

import org.bitcoinj.core.Coin;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TradeStatistics3Test {
    @Test
    public void isValidAcceptsHistoricalTradeAboveCurrentTradeLimit() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                50_000_000,
                Coin.parseCoin("0.25").value,
                "SEPA",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertTrue(tradeStatistics.isValid());
    }

    @Test
    public void isValidRejectsTradeAboveHistoricalSanityLimit() {
        TradeStatistics3 tradeStatistics = new TradeStatistics3("USD",
                50_000_000,
                TradeStatistics3.HISTORICAL_MAX_TRADE_AMOUNT + 1,
                "SEPA",
                System.currentTimeMillis(),
                null,
                null,
                (TreeMap<String, String>) null);

        assertFalse(tradeStatistics.isValid());
    }

    @Disabled("Not fixed yet")
    @Test
    public void allPaymentMethodsCoveredByWrapper() {
        Set<String> paymentMethodCodes = PaymentMethod.getPaymentMethods().stream()
                .map(PaymentMethod::getId)
                .collect(Collectors.toSet());

        Set<String> wrapperCodes = Arrays.stream(TradeStatistics3.PaymentMethodMapper.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of(), Sets.difference(paymentMethodCodes, wrapperCodes));
    }
}
