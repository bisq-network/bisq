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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TradeStatistics3Test {
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
