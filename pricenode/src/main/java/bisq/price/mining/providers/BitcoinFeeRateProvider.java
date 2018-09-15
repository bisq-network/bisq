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

import bisq.price.PriceController;
import bisq.price.mining.FeeRate;
import bisq.price.mining.FeeRateProvider;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
class BitcoinFeeRateProvider extends FeeRateProvider {

    private static final long MIN_FEE_RATE = 10; // satoshi/byte
    private static final long MAX_FEE_RATE = 1000;

    private static final int DEFAULT_MAX_BLOCKS = 2;
    private static final int DEFAULT_REFRESH_INTERVAL = 2;

    private final RestTemplate restTemplate = new RestTemplate();

    private final int maxBlocks;

    public BitcoinFeeRateProvider(Environment env) {
        super(Duration.ofMinutes(refreshInterval(env)));
        this.maxBlocks = maxBlocks(env);
    }

    protected FeeRate doGet() {
        return new FeeRate("BTC", getEstimatedFeeRate(), Instant.now().getEpochSecond());
    }

    private long getEstimatedFeeRate() {
        return getFeeRatePredictions()
            .filter(p -> p.get("maxDelay") <= maxBlocks)
            .findFirst()
            .map(p -> p.get("maxFee"))
            .map(r -> {
                log.info("latest fee rate prediction is {} sat/byte", r);
                return r;
            })
            .map(r -> Math.max(r, MIN_FEE_RATE))
            .map(r -> Math.min(r, MAX_FEE_RATE))
            .orElse(MIN_FEE_RATE);
    }

    private Stream<Map<String, Long>> getFeeRatePredictions() {
        return restTemplate.exchange(
            RequestEntity
                .get(UriComponentsBuilder
                    // now using /fees/list because /fees/recommended estimates were too high
                    .fromUriString("https://bitcoinfees.earn.com/api/v1/fees/list")
                    .build().toUri())
                .header("User-Agent", "") // required to avoid 403
                .build(),
            new ParameterizedTypeReference<Map<String, List<Map<String, Long>>>>() {
            }
        ).getBody().entrySet().stream()
            .flatMap(e -> e.getValue().stream());
    }

    private static Optional<String[]> args(Environment env) {
        return Optional.ofNullable(
            env.getProperty(CommandLinePropertySource.DEFAULT_NON_OPTION_ARGS_PROPERTY_NAME, String[].class));
    }

    private static int maxBlocks(Environment env) {
        return args(env)
            .filter(args -> args.length >= 1)
            .map(args -> Integer.valueOf(args[0]))
            .orElse(DEFAULT_MAX_BLOCKS);
    }

    private static long refreshInterval(Environment env) {
        return args(env)
            .filter(args -> args.length >= 2)
            .map(args -> Integer.valueOf(args[1]))
            .orElse(DEFAULT_REFRESH_INTERVAL);
    }


    @RestController
    class Controller extends PriceController {

        @GetMapping(path = "/getParams")
        public String getParams() {
            return String.format("%s;%s", maxBlocks, refreshInterval.toMillis());
        }
    }
}
