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

package bisq.apitest.method.payment;

import bisq.apitest.method.DockerMethodTest;

import protobuf.PaymentMethod;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class GetPaymentMethodsTest extends DockerMethodTest {

    @Test
    public void testGetPaymentMethods() {
        List<String> paymentMethodIds = aliceClient.getPaymentMethods()
                .stream()
                .map(PaymentMethod::getId)
                .collect(Collectors.toList());
        assertTrue(paymentMethodIds.size() >= 20,
                "expected at least 20 payment methods, got " + paymentMethodIds.size());
        // Stable canonical IDs that the rest of the test suite + downstream code
        // depend on. If any of these regress the size check alone would not catch it.
        assertTrue(paymentMethodIds.contains("F2F"), "F2F payment method missing");
        assertTrue(paymentMethodIds.contains("SEPA"), "SEPA payment method missing");
        assertTrue(paymentMethodIds.contains("REVOLUT"), "REVOLUT payment method missing");
        assertTrue(paymentMethodIds.contains("NATIONAL_BANK"), "NATIONAL_BANK payment method missing");
    }
}
