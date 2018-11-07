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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * High-level mining {@link FeeRate} operations.
 */
@Service
class FeeRateService {

    private final Set<FeeRateProvider> providers;

    public FeeRateService(Set<FeeRateProvider> providers) {
        this.providers = providers;
    }

    public Map<String, Object> getFees() {
        Map<String, Long> metadata = new HashMap<>();
        Map<String, Long> allFeeRates = new HashMap<>();

        providers.forEach(p -> {
            FeeRate feeRate = p.get();
            String currency = feeRate.getCurrency();
            if ("BTC".equals(currency)) {
                metadata.put("bitcoinFeesTs", feeRate.getTimestamp());
            }
            allFeeRates.put(currency.toLowerCase() + "TxFee", feeRate.getPrice());
        });

        return new HashMap<String, Object>() {{
            putAll(metadata);
            put("dataMap", allFeeRates);
        }};
    }
}
