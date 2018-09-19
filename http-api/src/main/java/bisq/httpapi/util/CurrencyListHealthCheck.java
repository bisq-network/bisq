/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.httpapi.util;

import bisq.httpapi.service.endpoint.MarketEndpoint;



import com.codahale.metrics.health.HealthCheck;

public class CurrencyListHealthCheck extends HealthCheck {

    /**
     * Check that the proxy returns a valid currencyList
     */
    @Override
    protected Result check() {
        if (MarketEndpoint.getCurrencyList().currencies.size() > 0)
            return Result.healthy();
        return Result.unhealthy("Size of currency list is 0");
    }
}
