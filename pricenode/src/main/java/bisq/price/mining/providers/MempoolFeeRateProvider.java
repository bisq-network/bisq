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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link FeeRateProvider} that interprets the Mempool API format to retrieve a mining
 * fee estimate. See https://mempool.space.
 */
abstract class MempoolFeeRateProvider extends FeeRateProvider {

    private static final int DEFAULT_MAX_BLOCKS = 2;
    private static final int DEFAULT_REFRESH_INTERVAL = 2;

    // Keys of properties defining the available Mempool API endpoints. To enable them,
    // simply uncomment and adjust the corresponding lines in application.properties
    private static final String MEMPOOL_HOSTNAME_KEY_1 = "bisq.price.mining.providers.mempoolHostname.1";
    private static final String MEMPOOL_HOSTNAME_KEY_2 = "bisq.price.mining.providers.mempoolHostname.2";
    private static final String MEMPOOL_HOSTNAME_KEY_3 = "bisq.price.mining.providers.mempoolHostname.3";
    private static final String MEMPOOL_HOSTNAME_KEY_4 = "bisq.price.mining.providers.mempoolHostname.4";
    private static final String MEMPOOL_HOSTNAME_KEY_5 = "bisq.price.mining.providers.mempoolHostname.5";

    private static final RestTemplate restTemplate = new RestTemplate();

    // TODO: As of the switch to the mempool.space API this field and related members are
    //  now dead code and should be removed, including removing the positional
    //  command-line argument from startup scripts. Operators need to be notified of this
    //  when it happens.
    private final int maxBlocks;

    protected Environment env;

    public MempoolFeeRateProvider(Environment env) {
        super(Duration.ofMinutes(refreshInterval(env)));
        this.env = env;
        this.maxBlocks = maxBlocks(env);
    }

    protected FeeRate doGet() {
        // Default value is the minimum rate. If the connection to the fee estimate
        // provider fails, we fall back to this value.
        try {
            return getEstimatedFeeRate();
        }
        catch (Exception e) {
            // Something happened with the connection
            log.error("Error retrieving bitcoin mining fee estimation: " + e.getMessage());
        }

        return new FeeRate("BTC", MIN_FEE_RATE_FOR_TRADING, MIN_FEE_RATE_FOR_WITHDRAWAL, Instant.now().getEpochSecond());
    }

    private FeeRate getEstimatedFeeRate() {
        Set<Map.Entry<String, Long>> feeRatePredictions = getFeeRatePredictions();
        long estimatedFeeRate = feeRatePredictions.stream()
                .filter(p -> p.getKey().equalsIgnoreCase("halfHourFee"))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(r -> Math.max(r, MIN_FEE_RATE_FOR_TRADING))
                .map(r -> Math.min(r, MAX_FEE_RATE))
                .orElse(MIN_FEE_RATE_FOR_TRADING);
        long economyFee = feeRatePredictions.stream()
                .filter(p -> p.getKey().equalsIgnoreCase("economyFee"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(MIN_FEE_RATE_FOR_WITHDRAWAL);
        log.info("Retrieved estimated mining fee of {} sat/vB and economyFee of {} sat/vB from {}", estimatedFeeRate, economyFee, getMempoolApiHostname());
        return new FeeRate("BTC", estimatedFeeRate, economyFee, Instant.now().getEpochSecond());
    }

    private Set<Map.Entry<String, Long>> getFeeRatePredictions() {
        return restTemplate.exchange(
            RequestEntity
                .get(UriComponentsBuilder
                    // See https://github.com/bisq-network/projects/issues/27
                    .fromUriString("https://" + getMempoolApiHostname() + "/api/v1/fees/recommended")
                    .build().toUri())
                .build(),
            new ParameterizedTypeReference<Map<String, Long>>() { }
        ).getBody().entrySet();
    }

    /**
     * Return the hostname of the fee estimation API endpoint. No prefix (https://), no
     * suffix (trailing slashes, etc).
     */
    protected abstract String getMempoolApiHostname();

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

    @Primary
    @Component
    @Order(1)
    public static class First extends MempoolFeeRateProvider {

        public First(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            // This is the primary instance, so if no API point is set in
            // application.properties file, then it defaults to mempool.space
            // This ensures there is at least one provider attempting to connect,
            // even if the properties file is corrupt or empty
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_1, "mempool.space");
        }
    }

    @Component
    @Order(2)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_2)
    public static class Second extends MempoolFeeRateProvider {

        public Second(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_2);
        }
    }

    @Component
    @Order(3)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_3)
    public static class Third extends MempoolFeeRateProvider {

        public Third(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_3);
        }
    }

    @Component
    @Order(4)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_4)
    public static class Fourth extends MempoolFeeRateProvider {

        public Fourth(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_4);
        }
    }

    @Component
    @Order(5)
    @ConditionalOnProperty(name = MEMPOOL_HOSTNAME_KEY_5)
    public static class Fifth extends MempoolFeeRateProvider {

        public Fifth(Environment env) {
            super(env);
        }

        protected String getMempoolApiHostname() {
            return env.getProperty(MEMPOOL_HOSTNAME_KEY_5);
        }
    }

    @RestController
    class Controller extends PriceController {

        @GetMapping(path = "/getParams")
        public String getParams() {
            return String.format("%s;%s", maxBlocks, refreshInterval.toMillis());
        }
    }
}
