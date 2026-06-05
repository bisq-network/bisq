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

import java.io.IOException;
import java.io.InputStream;

import java.util.HashSet;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import bisq.common.config.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DenyListTests {
    @Test
    void buildsClasspathResourceNameFromConfiguredNetwork() {
        assertEquals("denylist/btc_mainnet.denylist", DenyList.resourceName(new Config()));
    }

    @Test
    void loadsMainnetResourceFromConfiguredClasspath() {
        DenyList denyList = new DenyList(new Config());

        assertTrue(denyList.getNodeAddressesBannedFromTrading().contains("c7r6wr4ful5vr3ie.onion:9999"));
    }

    @Test
    void ignoreDenyListConfigSkipsResourceLoading() {
        DenyList denyList = new DenyList(new Config("--ignoreDenyList=true"));

        assertTrue(denyList.getNodeAddressesBannedFromTrading().isEmpty());
        assertTrue(denyList.getBannedSeedNodes().isEmpty());
        assertTrue(denyList.getRequiredVersionForTrading().isEmpty());
    }

    @Test
    void loadsMainnetResource() throws IOException {
        DenyList denyList = DenyList.fromProperties(loadMainnetProperties());

        assertTrue(denyList.getBannedCurrencies().contains("KYD"));
        assertTrue(denyList.getBannedPaymentMethods().contains("VENMO"));
        assertTrue(denyList.getNodeAddressesBannedFromTrading().contains("c7r6wr4ful5vr3ie.onion:9999"));
        assertTrue(denyList.getNodeAddressesBannedFromNetwork().contains("ggtrtroxansgcybc.onion:9999"));
        assertTrue(denyList.getBannedSeedNodes().contains("wizseedscybbttk4bmb2lzvbuk2jtect37lcpva4l3twktmkzemwbead.onion:8000"));
        assertTrue(denyList.getBannedBtcNodes().contains("165.227.34.198:8333"));
        assertTrue(denyList.getBannedPriceRelayNodes().contains("wizpriceje6q5tdrxkyiazsgu7irquiqjy2dptezqhrtu7l2qelqktid"));
        assertTrue(denyList.getBannedAutoConfExplorers().contains("explorer.monero.wiz.biz"));
        assertEquals("1.10.0", denyList.getRequiredVersionForTrading());
    }

    @Test
    void deDuplicatesValuesInEncounterOrder() throws IOException {
        DenyList denyList = DenyList.fromProperties(loadMainnetProperties());

        assertEquals(new HashSet<>(denyList.getNodeAddressesBannedFromTrading()).size(),
                denyList.getNodeAddressesBannedFromTrading().size());
        assertEquals("sqhl7bpblzazkteq.onion:9999",
                denyList.getNodeAddressesBannedFromTrading().get(25));
        assertEquals("fjmdtwhnrlrdd7xl.onion:9999",
                denyList.getNodeAddressesBannedFromTrading().get(26));
    }

    @Test
    void rejectsMalformedNodeAddresses() {
        Properties properties = new Properties();
        properties.setProperty("bannedSeedNodes", "missing-port.onion");

        assertThrows(IllegalArgumentException.class, () -> DenyList.fromProperties(properties));
    }

    @Test
    void parsedListsAreImmutable() throws IOException {
        DenyList denyList = DenyList.fromProperties(loadMainnetProperties());

        assertThrows(UnsupportedOperationException.class,
                () -> denyList.getBannedCurrencies().add("USD"));
    }

    private Properties loadMainnetProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("denylist/btc_mainnet.denylist")) {
            properties.load(inputStream);
        }
        return properties;
    }
}
