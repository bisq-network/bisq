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

package bisq.price.spot.providers;

import bisq.price.spot.ExchangeRate;
import bisq.price.spot.ExchangeRateProvider;
import bisq.common.util.Hex;

import org.knowm.xchange.bitcoinaverage.dto.marketdata.BitcoinAverageTicker;
import org.knowm.xchange.bitcoinaverage.dto.marketdata.BitcoinAverageTickers;

import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.common.base.Charsets;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.time.Duration;
import java.time.Instant;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * See the BitcoinAverage API documentation at https://apiv2.bitcoinaverage.com/#ticker-data-all
 */
public abstract class BitcoinAverage extends ExchangeRateProvider {

    /**
     * Max number of requests allowed per month on the BitcoinAverage developer plan.
     * Note the actual max value is 45,000; we use the more conservative value below to
     * ensure we do not exceed it. See https://bitcoinaverage.com/en/plans.
     */
    private static final double MAX_REQUESTS_PER_MONTH = 42_514;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String symbolSet;

    private String pubKey;
    private Mac mac;

    /**
     * @param symbolSet "global" or "local"; see https://apiv2.bitcoinaverage.com/#supported-currencies
     */
    public BitcoinAverage(String name, String prefix, double pctMaxRequests, String symbolSet, Environment env) {
        super(name, prefix, refreshIntervalFor(pctMaxRequests));
        this.symbolSet = symbolSet;
        this.pubKey = env.getRequiredProperty("BITCOIN_AVG_PUBKEY");
        this.mac = initMac(env.getRequiredProperty("BITCOIN_AVG_PRIVKEY"));
    }

    @Override
    public Set<ExchangeRate> doGet() {

        return getTickersKeyedByCurrency().entrySet().stream()
            .filter(e -> supportedCurrency(e.getKey()))
            .map(e ->
                new ExchangeRate(
                    e.getKey(),
                    e.getValue().getLast(),
                    e.getValue().getTimestamp(),
                    this.getName()
                )
            )
            .collect(Collectors.toSet());
    }

    private boolean supportedCurrency(String currencyCode) {
        // ignore Venezuelan bolivars as the "official" exchange rate is just wishful thinking
        // we should use this API with a custom provider instead: http://api.bitcoinvenezuela.com/1
        return !"VEF".equals(currencyCode);
    }

    private Map<String, BitcoinAverageTicker> getTickersKeyedByCurrency() {
        // go from a map with keys like "BTCUSD", "BTCVEF"
        return getTickersKeyedByCurrencyPair().entrySet().stream()
            // to a map with keys like "USD", "VEF"
            .collect(Collectors.toMap(e -> e.getKey().substring(3), Map.Entry::getValue));
    }

    private Map<String, BitcoinAverageTicker> getTickersKeyedByCurrencyPair() {
        return restTemplate.exchange(
            RequestEntity
                .get(UriComponentsBuilder
                    .fromUriString("https://apiv2.bitcoinaverage.com/indices/{symbol-set}/ticker/all?crypto=BTC")
                    .buildAndExpand(symbolSet)
                    .toUri())
                .header("X-signature", getAuthSignature())
                .build(),
            BitcoinAverageTickers.class
        ).getBody().getTickers();
    }

    protected String getAuthSignature() {
        String payload = String.format("%s.%s", Instant.now().getEpochSecond(), pubKey);
        return String.format("%s.%s", payload, Hex.encode(mac.doFinal(payload.getBytes(Charsets.UTF_8))));
    }

    private static Mac initMac(String privKey) {
        String algorithm = "HmacSHA256";
        SecretKey secretKey = new SecretKeySpec(privKey.getBytes(Charsets.UTF_8), algorithm);
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(secretKey);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Duration refreshIntervalFor(double pctMaxRequests) {
        long requestsPerMonth = (long) (MAX_REQUESTS_PER_MONTH * pctMaxRequests);
        return Duration.ofDays(31).dividedBy(requestsPerMonth);
    }


    @Component
    @Order(1)
    public static class Global extends BitcoinAverage {
        public Global(Environment env) {
            super("BTCA_G", "btcAverageG", 0.3, "global", env);
        }
    }


    @Component
    @Order(2)
    public static class Local extends BitcoinAverage {
        public Local(Environment env) {
            super("BTCA_L", "btcAverageL", 0.7, "local", env);
        }
    }
}
