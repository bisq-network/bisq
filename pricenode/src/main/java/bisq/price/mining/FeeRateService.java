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

package bisq.price.mining;

import org.springframework.stereotype.Service;

import java.time.Instant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-level mining {@link FeeRate} operations.
 */
@Service
class FeeRateService {

    private final List<FeeRateProvider> providers;

    /**
     * Construct a {@link FeeRateService} with a list of all {@link FeeRateProvider}
     * implementations discovered via classpath scanning.
     *
     * @param providers all {@link FeeRateProvider} implementations in ascending
     *                  order of precedence
     */
    public FeeRateService(List<FeeRateProvider> providers) {
        this.providers = providers;
    }

    public Map<String, Object> getFees() {
        Map<String, Long> metadata = new HashMap<>();
        Map<String, Long> allFeeRates = new HashMap<>();

        AtomicLong sumOfAllFeeRates = new AtomicLong();
        AtomicInteger amountOfFeeRates = new AtomicInteger();

        // Process each provider, retrieve and store their fee rate
        providers.forEach(p -> {
            FeeRate feeRate = p.get();
            String currency = feeRate.getCurrency();
            if ("BTC".equals(currency)) {
                sumOfAllFeeRates.getAndAdd(feeRate.getPrice());
                amountOfFeeRates.getAndAdd(1);
            }
        });

        // Calculate the average
        long averageFeeRate = (amountOfFeeRates.intValue() > 0)
                ? sumOfAllFeeRates.longValue() / amountOfFeeRates.intValue()
                : FeeRateProvider.MIN_FEE_RATE;

        // Make sure the returned value is within the min-max range
        averageFeeRate = Math.max(averageFeeRate, FeeRateProvider.MIN_FEE_RATE);
        averageFeeRate = Math.min(averageFeeRate, FeeRateProvider.MAX_FEE_RATE);

        // Prepare response: Add timestamp of now
        // Since this is an average, the timestamp is associated with when the moment in
        // time when the avg was computed
        metadata.put("bitcoinFeesTs", Instant.now().getEpochSecond());

        // Prepare response: Add the fee average
        allFeeRates.put("btcTxFee", averageFeeRate);

        // Build response
        return new HashMap<>() {{
            putAll(metadata);
            put("dataMap", allFeeRates);
        }};
    }
}
