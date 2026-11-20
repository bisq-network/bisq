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

import io.grpc.StatusRuntimeException;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accounts of the custom payment method and of the stablecoin payment methods are created in the desktop application
 * only, where the user confirms the risk warnings. The API must neither list these payment methods nor create such an
 * account. Read-only: every account creation is expected to fail.
 */
@Slf4j
public class ApiUnsupportedPaymentMethodsTest extends DockerMethodTest {

    private static final List<String> UNSUPPORTED_IDS = List.of("CUSTOM", "USDT_TRC20", "USDT_ERC20", "USDC_ERC20");

    @Test
    public void testUnsupportedPaymentMethodsAreNotListed() {
        List<String> paymentMethodIds = aliceClient.getPaymentMethods().stream()
                .map(PaymentMethod::getId)
                .collect(Collectors.toList());
        UNSUPPORTED_IDS.forEach(id ->
                assertFalse(paymentMethodIds.contains(id), id + " must not be listed by the api"));
    }

    @Test
    public void testPaymentAccountFormIsRefused() {
        UNSUPPORTED_IDS.forEach(id -> {
            Throwable exception = assertThrows(StatusRuntimeException.class,
                    () -> aliceClient.getPaymentAcctFormAsJson(id));
            assertUnsupported(id, exception);
        });
    }

    @Test
    public void testCreatePaymentAccountIsRefused() {
        UNSUPPORTED_IDS.forEach(id -> {
            String json = "{\n" +
                    "  \"paymentMethodId\": \"" + id + "\",\n" +
                    "  \"accountName\": \"Unsupported " + id + " " + System.nanoTime() + "\"\n" +
                    "}\n";
            Throwable exception = assertThrows(StatusRuntimeException.class,
                    () -> aliceClient.createPaymentAccount(json));
            assertUnsupported(id, exception);
        });
    }

    private static void assertUnsupported(String paymentMethodId, Throwable exception) {
        String expected = format("the api does not support %s payment accounts", paymentMethodId);
        assertTrue(exception.getMessage().contains(expected),
                "expected '" + expected + "', got: " + exception.getMessage());
    }
}
