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

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import java.net.URL;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DenyListTests {
    // Loaded explicitly, so the values asserted below do not depend on the bundled deny lists.
    private static final String FIXTURE_RESOURCE = "denylist/unit_test.denylist";
    // Loaded by apitest/docker/run-e2e-tests.sh through --denyListResource.
    private static final String E2E_RESOURCE = "denylist/btc_regtest_e2e.denylist";

    @Test
    void buildsClasspathResourceNameFromConfiguredNetwork() {
        assertEquals("denylist/btc_mainnet.denylist", DenyList.resourceName(new Config()));
    }

    @Test
    void loadsTheResourceOfTheConfiguredNetwork() {
        DenyList denyList = new DenyList(new Config());

        assertEquals(lists(DenyList.fromProperties(loadProperties("denylist/btc_mainnet.denylist"))),
                lists(denyList));
    }

    @Test
    void loadsExplicitClasspathResourceOverride() {
        Config config = new Config("--denyListResource=" + FIXTURE_RESOURCE);
        DenyList denyList = new DenyList(config);

        assertEquals(FIXTURE_RESOURCE, DenyList.resolveResourceName(config));
        assertEquals(List.of("KYD"), denyList.getBannedCurrencies());
    }

    @Test
    void rejectsMissingExplicitClasspathResourceOverride() {
        Config config = new Config("--denyListResource=denylist/missing.denylist");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DenyList(config));

        assertTrue(exception.getMessage().contains("denylist/missing.denylist"));
    }

    @Test
    void ignoreDenyListConfigSkipsResourceLoading() {
        DenyList denyList = new DenyList(new Config("--ignoreDenyList=true"));

        assertTrue(denyList.getNodeAddressesBannedFromTrading().isEmpty());
        assertTrue(denyList.getBannedSeedNodes().isEmpty());
    }

    @Test
    void readsEveryListFromResource() {
        DenyList denyList = new DenyList(new Config("--denyListResource=" + FIXTURE_RESOURCE));

        assertEquals(List.of("tradingpeer.onion:9999"), denyList.getNodeAddressesBannedFromTrading());
        assertEquals(List.of("networkpeer.onion:9999"), denyList.getNodeAddressesBannedFromNetwork());
        assertEquals(List.of("KYD"), denyList.getBannedCurrencies());
        assertEquals(List.of("VENMO"), denyList.getBannedPaymentMethods());
        assertEquals(List.of("02a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192"),
                denyList.getBannedAccountWitnessSignerPubKeys());
        assertEquals(List.of("mediator.onion:9999"), denyList.getBannedMediators());
        assertEquals(List.of("refundagent.onion:9999"), denyList.getBannedRefundAgents());
        assertEquals(List.of("seednode.onion:8000"), denyList.getBannedSeedNodes());
        assertEquals(List.of("pricerelayserviceid"), denyList.getBannedPriceRelayNodes());
        assertEquals(List.of("203.0.113.1:8333"), denyList.getBannedBtcNodes());
        assertEquals(List.of("explorer.example"), denyList.getBannedAutoConfExplorers());
    }

    // The bundled resources are checked for their format only, so that changing an entry does not
    // require a test change. Each check covers a failure that DenyList itself would hide or that
    // would only surface at application start.
    @ParameterizedTest
    @MethodSource("bundledResources")
    void bundledResourceIsNotShadowedByATestResource(String resource) throws IOException {
        List<URL> urls = Collections.list(DenyListTests.class.getClassLoader().getResources(resource));

        assertEquals(1, urls.size(), resource + " exists more than once on the test classpath: " + urls);
    }

    @ParameterizedTest
    @MethodSource("bundledResources")
    void bundledResourceHasValidNodeAddresses(String resource) {
        // The constructor rejects a malformed node address, which would stop the application start.
        new DenyList(new Config("--denyListResource=" + resource));
    }

    @ParameterizedTest
    @MethodSource("bundledResources")
    void bundledResourceUsesOnlySupportedKeys(String resource) {
        // DenyList ignores unknown keys, so a misspelled key would silently drop its bans.
        for (String key : loadProperties(resource).stringPropertyNames()) {
            Properties singleKey = new Properties();
            singleKey.setProperty(key, "supported.onion:9999");

            assertFalse(lists(DenyList.fromProperties(singleKey)).values().stream().allMatch(List::isEmpty),
                    resource + " uses a key that DenyList does not read: " + key);
        }
    }

    @ParameterizedTest
    @MethodSource("bundledResources")
    void bundledResourceListsContainNoDuplicates(String resource) {
        // DenyList removes duplicates while parsing, so they must be checked in the raw values.
        Properties properties = loadProperties(resource);
        for (String key : properties.stringPropertyNames()) {
            List<String> values = Arrays.stream(properties.getProperty(key).split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            Set<String> seen = new HashSet<>();
            List<String> duplicates = new ArrayList<>();
            values.forEach(value -> {
                if (!seen.add(value)) {
                    duplicates.add(value);
                }
            });

            assertTrue(duplicates.isEmpty(), resource + " lists these " + key + " values twice: " + duplicates);
        }
    }

    @Test
    void deDuplicatesValuesInEncounterOrder() {
        Properties properties = new Properties();
        properties.setProperty("bannedPaymentMethods", "VENMO, CASH_APP, VENMO, OK_PAY, CASH_APP");

        DenyList denyList = DenyList.fromProperties(properties);

        assertEquals(List.of("VENMO", "CASH_APP", "OK_PAY"), denyList.getBannedPaymentMethods());
    }

    @Test
    void rejectsMalformedNodeAddresses() {
        Properties properties = new Properties();
        properties.setProperty("bannedSeedNodes", "missing-port.onion");

        assertThrows(IllegalArgumentException.class, () -> DenyList.fromProperties(properties));
    }

    @Test
    void parsedListsAreImmutable() {
        DenyList denyList = DenyList.fromProperties(loadProperties(FIXTURE_RESOURCE));

        assertThrows(UnsupportedOperationException.class,
                () -> denyList.getBannedCurrencies().add("USD"));
    }

    static Stream<String> bundledResources() {
        Stream<String> networkResources = Arrays.stream(BaseCurrencyNetwork.values())
                .map(DenyList::resourceName)
                .filter(resource -> DenyListTests.class.getClassLoader().getResource(resource) != null);
        return Stream.concat(networkResources, Stream.of(E2E_RESOURCE));
    }

    private static Properties loadProperties(String resource) {
        Properties properties = new Properties();
        try (InputStream inputStream = DenyListTests.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(inputStream != null, "resource not found: " + resource);
            properties.load(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> lists(DenyList denyList) {
        Map<String, List<String>> lists = new TreeMap<>();
        for (Method method : DenyList.class.getMethods()) {
            if (method.getName().startsWith("get") &&
                    method.getParameterCount() == 0 &&
                    List.class.equals(method.getReturnType())) {
                try {
                    lists.put(method.getName(), (List<String>) method.invoke(denyList));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Cannot read " + method.getName(), e);
                }
            }
        }
        return lists;
    }
}
