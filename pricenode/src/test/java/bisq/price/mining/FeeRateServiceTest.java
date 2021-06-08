package bisq.price.mining;

import bisq.price.mining.providers.MempoolFeeRateProviderTest;

import bisq.common.config.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;

import static bisq.price.mining.providers.MempoolFeeRateProviderTest.buildDummyReachableMempoolFeeRateProvider;
import static bisq.price.mining.providers.MempoolFeeRateProviderTest.buildDummyUnreachableMempoolFeeRateProvider;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests the {@link bisq.price.mining.FeeRateService}, which can aggregate data from
 * several {@link FeeRateProvider}s.
 * @see MempoolFeeRateProviderTest
 */
public class FeeRateServiceTest {

    private static final Logger log = LoggerFactory.getLogger(FeeRateServiceTest.class);

    @Test
    public void getFees_noWorkingProvider() {
        // Several providers, but all unreachable
        List<FeeRateProvider> listOfProviders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                listOfProviders.add(buildDummyUnreachableMempoolFeeRateProvider());
            } catch (Exception e) {
                // Expected
                log.info("We encountered an expected exception: " + e.getMessage());
            }
        }
        FeeRateService service = new FeeRateService(listOfProviders);

        Map<String, Object> retrievedData = service.getFees();

        // Even with no working providers, we expect the service to return pre-configured
        // minimum fee rate
        doSanityChecksForRetrievedData(retrievedData, FeeRateProvider.MIN_FEE_RATE_FOR_TRADING);
    }

    @Test
    public void getFees_singleProvider_feeBelowMin() {
        // One working provider, which returns a fee lower than the minimum
        long providerFee = FeeRateProvider.MIN_FEE_RATE_FOR_TRADING - 3;
        FeeRateService service = new FeeRateService(
                Collections.singletonList(
                        buildDummyReachableMempoolFeeRateProvider(providerFee)));

        Map<String, Object> retrievedData = service.getFees();

        // When the provider returns a value below the expected min, the service should
        // return the min
        doSanityChecksForRetrievedData(retrievedData, FeeRateProvider.MIN_FEE_RATE_FOR_TRADING);
    }

    @Test
    public void getFees_singleProvider_feeAboveMax() {
        // One working provider, which returns a fee greater than the maximum
        long providerFee = FeeRateProvider.MAX_FEE_RATE + 13;
        FeeRateService service = new FeeRateService(
                Collections.singletonList(
                        buildDummyReachableMempoolFeeRateProvider(providerFee)));

        Map<String, Object> retrievedData = service.getFees();

        // When the provider returns a value above the expected max, the service should
        // return the max
        doSanityChecksForRetrievedData(retrievedData, FeeRateProvider.MAX_FEE_RATE);
    }

    @Test
    public void getFees_multipleProviders() {
        // 3 providers, returning 1xMIN, 2xMIN, 3xMIN
        FeeRateService service = new FeeRateService(asList(
                buildDummyReachableMempoolFeeRateProvider(FeeRateProvider.MIN_FEE_RATE_FOR_TRADING * 1),
                buildDummyReachableMempoolFeeRateProvider(FeeRateProvider.MIN_FEE_RATE_FOR_TRADING * 2),
                buildDummyReachableMempoolFeeRateProvider(FeeRateProvider.MIN_FEE_RATE_FOR_TRADING * 3)));

        Map<String, Object> retrievedData = service.getFees();

        // The service should then return the average, which is 2xMIN
        doSanityChecksForRetrievedData(retrievedData, FeeRateProvider.MIN_FEE_RATE_FOR_TRADING * 2);
    }

    /**
     * Performs a few basic sanity checks on the returned data object
     */
    private void doSanityChecksForRetrievedData(Map<String, Object> retrievedData, long expectedFeeRate) {
        // Check if the response has the expected format. Since the timestamp is that of
        // the average (not that of the individual fee rates reported by the individual
        // providers), we always expect a non-zero timestamp
        assertNotEquals(0L, retrievedData.get(Config.BTC_FEES_TS));

        Map<String, String> retrievedDataMap = (Map<String, String>) retrievedData.get(Config.LEGACY_FEE_DATAMAP);
        assertEquals(2, retrievedDataMap.size());
        assertEquals(expectedFeeRate, retrievedDataMap.get(Config.BTC_TX_FEE));
    }
}
