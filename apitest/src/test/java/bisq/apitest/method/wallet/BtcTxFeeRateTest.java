package bisq.apitest.method.wallet;

import bisq.core.api.model.TxFeeRateInfo;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        TxFeeRateInfo txFeeRateInfo = getTxFeeRate(alicedaemon);
        log.debug("{} -> Fee rate with no preference: {}", testName(testInfo), txFeeRateInfo);

        assertFalse(txFeeRateInfo.isUseCustomTxFeeRate());
        assertTrue(txFeeRateInfo.getStdTxFeeRate() > 0);
    }

    @Test
    @Order(2)
    public void testSetTxFeeRate(final TestInfo testInfo) {
        TxFeeRateInfo txFeeRateInfo = setTxFeeRate(alicedaemon, 10);
        log.debug("{} -> Fee rates with custom preference: {}", testName(testInfo), txFeeRateInfo);

        assertTrue(txFeeRateInfo.isUseCustomTxFeeRate());
        assertEquals(10, txFeeRateInfo.getCustomTxFeeRate());
        assertTrue(txFeeRateInfo.getStdTxFeeRate() > 0);
    }

    @Test
    @Order(3)
    public void testUnsetTxFeeRate(final TestInfo testInfo) {
        TxFeeRateInfo txFeeRateInfo = unsetTxFeeRate(alicedaemon);
        log.debug("{} -> Fee rate with no preference: {}", testName(testInfo), txFeeRateInfo);

        assertFalse(txFeeRateInfo.isUseCustomTxFeeRate());
        assertTrue(txFeeRateInfo.getStdTxFeeRate() > 0);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
