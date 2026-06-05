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

package bisq.core.filter;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
@Singleton
public class DenyList {
    private static final String RESOURCE_DIRECTORY = "denylist";
    private static final String RESOURCE_EXTENSION = ".denylist";
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

    private final List<String> nodeAddressesBannedFromTrading;
    private final List<String> nodeAddressesBannedFromNetwork;
    private final List<String> bannedCurrencies;
    private final List<String> bannedPaymentMethods;
    private final List<String> bannedAccountWitnessSignerPubKeys;
    private final List<String> bannedMediators;
    private final List<String> bannedRefundAgents;
    private final List<String> bannedSeedNodes;
    private final List<String> bannedPriceRelayNodes;
    private final List<String> bannedBtcNodes;
    private final List<String> bannedAutoConfExplorers;
    private final String requiredVersionForTrading;

    @Inject
    public DenyList(Config config) {
        this(config.ignoreDenyList ? new Properties() : loadProperties(resourceName(config)));
        if (config.ignoreDenyList) {
            log.info("DenyList resource loading disabled by {}", Config.IGNORE_DENY_LIST);
        }
    }

    @VisibleForTesting
    DenyList(Properties properties) {
        nodeAddressesBannedFromTrading = readNodeAddressList(properties, "nodeAddressesBannedFromTrading");
        nodeAddressesBannedFromNetwork = readNodeAddressList(properties, "nodeAddressesBannedFromNetwork");
        bannedCurrencies = readList(properties, "bannedCurrencies");
        bannedPaymentMethods = readList(properties, "bannedPaymentMethods");
        bannedAccountWitnessSignerPubKeys = readList(properties, "bannedAccountWitnessSignerPubKeys");
        bannedMediators = readNodeAddressList(properties, "bannedMediators");
        bannedRefundAgents = readNodeAddressList(properties, "bannedRefundAgents");
        bannedSeedNodes = readNodeAddressList(properties, "bannedSeedNodes");
        bannedPriceRelayNodes = readList(properties, "bannedPriceRelayNodes");
        bannedBtcNodes = readNodeAddressList(properties, "bannedBtcNodes");
        bannedAutoConfExplorers = readList(properties, "bannedAutoConfExplorers");
        requiredVersionForTrading = properties.getProperty("requiredVersionForTrading", "").trim();
    }

    public static DenyList empty() {
        return new DenyList(new Properties());
    }

    @VisibleForTesting
    public static DenyList fromProperties(Properties properties) {
        return new DenyList(properties);
    }

    @VisibleForTesting
    static String resourceName(Config config) {
        return RESOURCE_DIRECTORY + "/" +
                config.getBaseCurrencyNetwork().name().toLowerCase(Locale.ENGLISH) +
                RESOURCE_EXTENSION;
    }

    private static Properties loadProperties(String resourceName) {
        Properties properties = new Properties();
        try (InputStream inputStream = DenyList.class.getResourceAsStream("/" + resourceName)) {
            if (inputStream == null) {
                log.info("No DenyList resource found at {}", resourceName);
                return properties;
            }
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load DenyList resource " + resourceName, e);
        }
    }

    private static List<String> readNodeAddressList(Properties properties, String key) {
        List<String> values = readList(properties, key);
        values.forEach(value -> {
            try {
                new NodeAddress(value);
            } catch (Throwable t) {
                throw new IllegalArgumentException("Invalid DenyList node address in " + key + ": " + value, t);
            }
        });
        return values;
    }

    private static List<String> readList(Properties properties, String key) {
        Set<String> values = new LinkedHashSet<>();
        COMMA_SPLITTER.split(properties.getProperty(key, "")).forEach(values::add);
        return List.copyOf(values);
    }

    boolean hasRequiredVersionForTrading() {
        return !requiredVersionForTrading.isEmpty();
    }
}
