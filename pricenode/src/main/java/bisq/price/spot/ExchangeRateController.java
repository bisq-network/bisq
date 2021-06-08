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

import bisq.price.PriceController;
import bisq.price.mining.FeeRateService;

import bisq.common.config.Config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class ExchangeRateController extends PriceController {

    private final ExchangeRateService exchangeRateService;
    private final FeeRateService feeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService, FeeRateService feeRateService) {
        this.exchangeRateService = exchangeRateService;
        this.feeRateService = feeRateService;
    }

    @GetMapping(path = "/getAllMarketPrices")
    public Map<String, Object> getAllMarketPrices() {
        Map<String, Object> retVal = exchangeRateService.getAllMarketPrices();

        // add the fee info to results
        feeRateService.getFees().forEach((key, value) -> {
            retVal.put(translateFieldName(key), value);
        });

        return retVal;
    }

    static String translateFieldName(String name) {
        if (name.equals(Config.LEGACY_FEE_DATAMAP))
            name = Config.BTC_FEE_INFO;                 // name changed for clarity
        return name;
    }
}
