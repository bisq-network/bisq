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

package bisq.price.mining.providers;

import bisq.price.mining.FeeRate;
import bisq.price.mining.FeeRateProvider;

import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertTrue;

/**
 * Tests specific to a {@link MempoolFeeRateProvider} which queries one API endpoint. For
 * tests related to managing parallel fee API endpoints, see
 * {@link bisq.price.mining.FeeRateServiceTest}
 */
public class MempoolFeeRateProviderTest {

    private static final Environment env = new StandardEnvironment();

    @Test
    public void doGet_successfulCall() {
        MempoolFeeRateProvider feeRateProvider = new MempoolFeeRateProvider.First(env);

        // Make a call to the API, retrieve the recommended fee rate
        // If the API call fails, or the response body cannot be parsed, the test will
        // fail with an exception
        FeeRate retrievedFeeRate = feeRateProvider.doGet();

        // Check that the FeeRateProvider returns a fee within the defined parameters
        assertTrue(retrievedFeeRate.getPrice() >= FeeRateProvider.MIN_FEE_RATE_FOR_TRADING);
        assertTrue(retrievedFeeRate.getPrice() <= FeeRateProvider.MAX_FEE_RATE);
    }

    /**
     * Simulates a reachable provider, which successfully returns an API response
     */
    public static FeeRateProvider buildDummyReachableMempoolFeeRateProvider(long feeRate) {
        MempoolFeeRateProvider dummyProvider = new MempoolFeeRateProvider.First(env) {
            @Override
            protected FeeRate doGet() {
                return new FeeRate("BTC", feeRate, MIN_FEE_RATE_FOR_WITHDRAWAL, Instant.now().getEpochSecond());
            }
        };

        // Initialize provider
        dummyProvider.start();
        try {
            sleep(1000);
        } catch (InterruptedException e) { }
        dummyProvider.stop();

        return dummyProvider;
    }

    /**
     * Simulates an unreachable provider, which for whatever reason cannot deliver a
     * response to the API. Reasons for that could be: host went offline, connection
     * timeout, connection cannot be established (expired certificate), etc.
     */
    public static FeeRateProvider buildDummyUnreachableMempoolFeeRateProvider() throws RestClientException {
        MempoolFeeRateProvider dummyProvider = new MempoolFeeRateProvider.First(env) {
            @Override
            protected FeeRate doGet() {
                throw new RestClientException("Simulating connection error when trying to reach API endpoint");
            }
        };

        // Initialize provider
        dummyProvider.start();
        try {
            sleep(1000);
        } catch (InterruptedException e) { }
        dummyProvider.stop();

        return dummyProvider;
    }
}
