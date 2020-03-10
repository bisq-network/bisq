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

package bisq.price.spot;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class ExchangeRateServiceTest {

    @Test
    public void getAllMarketPrices_withNoExchangeRates_logs_Exception() {
        ExchangeRateProvider dummyProvider = new ExchangeRateProvider("Dummy", "YUM", Duration.ofDays(1)) {

            @Override
            public boolean isRunning() {
                return true;
            }

            @Override
            protected Set<ExchangeRate> doGet() {
                return Collections.emptySet();
            }
        };

        dummyProvider.start();
        dummyProvider.stop();

        List<ExchangeRateProvider> providerList = new ArrayList<>(Collections.singleton(dummyProvider));

        ExchangeRateService service = new ExchangeRateService(providerList);

        service.getAllMarketPrices();
    }

    @Test
    public void getAllMarketPrices_withSingleExchangeRate() {
        ExchangeRateProvider dummyProvider = new ExchangeRateProvider("Dummy", "YUM", Duration.ofDays(1)) {

            @Override
            public boolean isRunning() {
                return true;
            }

            @Override
            protected Set<ExchangeRate> doGet() {
                HashSet<ExchangeRate> exchangeRates = new HashSet<>();
                exchangeRates.add(new ExchangeRate("DUM", 0, 0L, "Dummy"));
                return exchangeRates;
            }
        };

        dummyProvider.start();
        dummyProvider.stop();

        List<ExchangeRateProvider> providerList = new ArrayList<>(Collections.singleton(dummyProvider));

        ExchangeRateService service = new ExchangeRateService(providerList);

        service.getAllMarketPrices();
    }
}
