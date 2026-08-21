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

package bisq.apitest.method.wallet;

import bisq.apitest.method.DockerMethodTest;
import bisq.core.api.model.TxFeeRateInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class BtcTxFeeRateTest extends DockerMethodTest {

    @AfterEach
    public void resetFeeRate() {
        // Restore the daemon's default fee preference so later tests are unaffected.
        // Failure here is meaningful — it indicates a daemon regression or leaked
        // custom fee state — so surface it as an AssertionError rather than swallow.
        try {
            aliceClient.unsetTxFeeRate();
        } catch (RuntimeException ex) {
            throw new AssertionError("failed to reset tx fee rate in @AfterEach", ex);
        }
    }

    @Test
    @Order(1)
    public void testGetTxFeeRate(final TestInfo testInfo) {
        var txFeeRateInfo = TxFeeRateInfo.fromProto(aliceClient.getTxFeeRate());
        log.debug("{} -> Fee rate with no preference: {}", testName(testInfo), txFeeRateInfo);

        assertFalse(txFeeRateInfo.isUseCustomTxFeeRate());
        assertTrue(txFeeRateInfo.getFeeServiceRate() > 0);
    }

    @Test
    @Order(2)
    public void testSetInvalidTxFeeRateShouldThrowException(final TestInfo testInfo) {
        var currentTxFeeRateInfo = TxFeeRateInfo.fromProto(aliceClient.getTxFeeRate());
        Throwable exception = assertThrows(StatusRuntimeException.class, () -> aliceClient.setTxFeeRate(1));
        String expectedExceptionMessage =
                format("INVALID_ARGUMENT: tx fee rate preference must be >= %d sats/byte",
                        currentTxFeeRateInfo.getMinFeeServiceRate());
        assertEquals(expectedExceptionMessage, exception.getMessage());
    }

    @Test
    @Order(3)
    public void testSetValidTxFeeRate(final TestInfo testInfo) {
        var currentTxFeeRateInfo = TxFeeRateInfo.fromProto(aliceClient.getTxFeeRate());
        var customFeeRate = currentTxFeeRateInfo.getMinFeeServiceRate() + 5;
        var txFeeRateInfo = TxFeeRateInfo.fromProto(aliceClient.setTxFeeRate(customFeeRate));
        log.debug("{} -> Fee rates with custom preference: {}", testName(testInfo), txFeeRateInfo);

        assertTrue(txFeeRateInfo.isUseCustomTxFeeRate());
        assertEquals(customFeeRate, txFeeRateInfo.getCustomTxFeeRate());
        assertTrue(txFeeRateInfo.getFeeServiceRate() > 0);
    }

    @Test
    @Order(4)
    public void testUnsetTxFeeRate(final TestInfo testInfo) {
        var txFeeRateInfo = TxFeeRateInfo.fromProto(aliceClient.unsetTxFeeRate());
        log.debug("{} -> Fee rate with no preference: {}", testName(testInfo), txFeeRateInfo);

        assertFalse(txFeeRateInfo.isUseCustomTxFeeRate());
        assertTrue(txFeeRateInfo.getFeeServiceRate() > 0);
    }

    private String testName(TestInfo testInfo) {
        return testInfo.getTestMethod().map(java.lang.reflect.Method::getName).orElse("unknown");
    }
}
