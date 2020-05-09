package bisq.price.mining;

import bisq.price.TestBase;
import bisq.price.mining.providers.BitcoinFeeRateProvider;

import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests the {@link bisq.price.mining.FeeRateService}, which can aggregate data from several {@link FeeRateProvider}s
 * <br/><br/>
 * @see bisq.price.mining.providers.BitcoinFeeRateProviderTest
 */
public class FeeRateServiceTest extends TestBase {

    @Test
    public void getFees_noWorkingProvider() {
        // Several providers, but all unreachable
        List<FeeRateProvider> listOfProviders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                listOfProviders.add(buildDummyUnreachableBitcoinFeeRateProvider());
            } catch (Exception e) {
                // Expected
                log.info("We encountered an expected exception: " + e.getMessage());
            }
        }
        FeeRateService service = new FeeRateService(listOfProviders);

        Map<String, Object> retrievedData = service.getFees();

        // Even with no working providers, we expect the service to return pre-configured minimum fee rate
        doSanityChecksForRetrievedData(retrievedData, BitcoinFeeRateProvider.MIN_FEE_RATE);
    }

    @Test
    public void getFees_singleProvider_feeBelowMin() {
        // One working provider, which returns a fee lower than the minimum
        long providerFee = BitcoinFeeRateProvider.MIN_FEE_RATE - 3;
        FeeRateService service = new FeeRateService(
                Collections.singletonList(
                        buildDummyReachableBitcoinFeeRateProvider(providerFee)));

        Map<String, Object> retrievedData = service.getFees();

        // When the provider returns a value below the expected min, the service should return the min
        doSanityChecksForRetrievedData(retrievedData, BitcoinFeeRateProvider.MIN_FEE_RATE);
    }

    @Test
    public void getFees_singleProvider_feeAboveMax() {
        // One working provider, which returns a fee greater than the maximum
        long providerFee = BitcoinFeeRateProvider.MAX_FEE_RATE + 13;
        FeeRateService service = new FeeRateService(
                Collections.singletonList(
                        buildDummyReachableBitcoinFeeRateProvider(providerFee)));

        Map<String, Object> retrievedData = service.getFees();

        // When the provider returns a value above the expected max, the service should return the max
        doSanityChecksForRetrievedData(retrievedData, BitcoinFeeRateProvider.MAX_FEE_RATE);
    }

    @Test
    public void getFees_multipleProviders() {
        // 3 providers, returning 1xMIN, 2xMIN, 3xMIN
        FeeRateService service = new FeeRateService(
                asList(
                        buildDummyReachableBitcoinFeeRateProvider(BitcoinFeeRateProvider.MIN_FEE_RATE * 1),
                        buildDummyReachableBitcoinFeeRateProvider(BitcoinFeeRateProvider.MIN_FEE_RATE * 2),
                        buildDummyReachableBitcoinFeeRateProvider(BitcoinFeeRateProvider.MIN_FEE_RATE * 3)));

        Map<String, Object> retrievedData = service.getFees();

        // The service should then return the average, which is 2xMIN
        doSanityChecksForRetrievedData(retrievedData, BitcoinFeeRateProvider.MIN_FEE_RATE * 2);
    }

    /**
     * Performs a few basic sanity checks on the returned data object
     *
     * @param retrievedData
     * @param expectedFeeRate
     */
    private void doSanityChecksForRetrievedData(Map<String, Object> retrievedData, long expectedFeeRate) {
        // Check if the response has the expected format
        // Since the timestamp is that of the average (not that of the individual fee rates reported
        // by the individual providers), we always expect a non-zero timestamp
        assertNotEquals(0L, retrievedData.get("bitcoinFeesTs"));

        Map<String, String> retrievedDataMap = (Map<String, String>) retrievedData.get("dataMap");
        assertEquals(1, retrievedDataMap.size());
        assertEquals(expectedFeeRate, retrievedDataMap.get("btcTxFee"));
    }

    /**
     * Simulates a reachable provider, which successfully returns an API response
     * @param feeRate
     * @return
     */
    private BitcoinFeeRateProvider buildDummyReachableBitcoinFeeRateProvider(long feeRate) {
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        BitcoinFeeRateProvider dummyProvider = new BitcoinFeeRateProvider(ctx.getEnvironment()) {
            @Override
            protected FeeRate doGet() {
                return new FeeRate("BTC", feeRate, Instant.now().getEpochSecond());
            }
        };

        // Initialize provider
        dummyProvider.start();
        dummyProvider.stop();

        return dummyProvider;
    }

    /**
     * Simulates an unreachable provider, which for whatever reason cannot deliver a response to the API.<br/><br/>
     * Reasons for that could be: host went offline, connection timeout, connection cannot be established (expired
     * certificate), etc.
     *
     * @return
     */
    private BitcoinFeeRateProvider buildDummyUnreachableBitcoinFeeRateProvider() throws RestClientException{
        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        BitcoinFeeRateProvider dummyProvider = new BitcoinFeeRateProvider(ctx.getEnvironment()) {
            @Override
            protected FeeRate doGet() {
                throw new RestClientException("Simulating connection error when trying to reach API endpoint");
            }
        };

        // Initialize provider
        dummyProvider.start();
        dummyProvider.stop();

        return dummyProvider;
    }
}
