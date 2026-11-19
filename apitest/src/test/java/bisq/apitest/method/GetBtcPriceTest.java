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

package bisq.apitest.method;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.time.Duration;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * getbtcprice answers from the cache of the price feed service and does not wait for a
 * price provider. The stack has no price node of its own: the daemons use the default
 * provider, which may or may not be reachable from the containers. So a call must
 * return a price or fail with UNAVAILABLE, and in both cases at once. Up to v1.10.11 a
 * call without prices failed with UNKNOWN ("price feed service has no prices"), and with
 * prices it waited for a new request to the provider.
 *
 * <p>The daemon allows one getmarketprice call per second, so the calls are spaced.
 */
@Slf4j
public class GetBtcPriceTest extends DockerMethodTest {
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);
    private static final long RATE_LIMIT_GAP_MS = 1_100;

    @Test
    public void testPriceRequestsAnswerFromTheCache() {
        for (String currencyCode : List.of("USD", "EUR", "XMR")) {
            sleep(RATE_LIMIT_GAP_MS);
            assertTimeoutPreemptively(ANSWER_TIMEOUT, () -> assertPriceOrUnavailable(currencyCode),
                    currencyCode + " price request did not answer at once");
        }
    }

    private static void assertPriceOrUnavailable(String currencyCode) {
        try {
            double price = aliceClient.getBtcPrice(currencyCode);
            assertTrue(price > 0, currencyCode + " price must be positive, was " + price);
        } catch (StatusRuntimeException exception) {
            assertEquals(Status.Code.UNAVAILABLE, exception.getStatus().getCode(),
                    "unexpected failure for " + currencyCode + ": " + exception.getMessage());
            assertEquals("UNAVAILABLE: " + currencyCode + " price is not available",
                    exception.getMessage());
        }
    }
}
