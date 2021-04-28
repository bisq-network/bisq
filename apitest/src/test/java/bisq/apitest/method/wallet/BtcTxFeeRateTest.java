package bisq.apitest.method.wallet;

import bisq.core.api.model.TxFeeRateInfo;

import io.grpc.StatusRuntimeException;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.MethodOrderer.OrderAnnotation;



import bisq.apitest.method.MethodTest;

@Disabled
@Slf4j
@TestMethodOrder(OrderAnnotation.class)
public class BtcTxFeeRateTest extends MethodTest {

    @BeforeAll
    public static void setUp() {
        startSupportingApps(false,
                true,
                bitcoind,
                seednode,
                alicedaemon);
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
                format("UNKNOWN: tx fee rate preference must be >= %d sats/byte",
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

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
