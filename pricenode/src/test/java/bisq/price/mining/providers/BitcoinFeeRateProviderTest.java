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

import org.springframework.context.support.GenericXmlApplicationContext;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BitcoinFeeRateProviderTest {

    @Test
    public void doGet_successfulCall() {

        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext();
        BitcoinFeeRateProvider feeRateProvider = new BitcoinFeeRateProvider(ctx.getEnvironment());

        // Make a call to the API, retrieve the recommended fee rate
        // If the API call fails, or the response body cannot be parsed, the test will fail with an exception
        FeeRate retrievedFeeRate = feeRateProvider.doGet();

        // Check that the FeeRateProvider returns a fee within the defined parameters
        assertTrue(retrievedFeeRate.getPrice() >= BitcoinFeeRateProvider.MIN_FEE_RATE);
        assertTrue(retrievedFeeRate.getPrice() <= BitcoinFeeRateProvider.MAX_FEE_RATE);
    }

}
