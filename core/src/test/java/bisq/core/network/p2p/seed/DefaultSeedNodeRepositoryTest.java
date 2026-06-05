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

package bisq.core.network.p2p.seed;

import bisq.core.filter.DenyList;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultSeedNodeRepositoryTest {

    @Test
    public void getSeedNodes() {
        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(new Config());
        assertFalse(DUT.getSeedNodeAddresses().isEmpty());
    }

    @Test
    public void manualSeedNodes() {
        String seed1 = "asdf:8001";
        String seed2 = "fdsa:6001";
        String seedNodesOption = format("--%s=%s,%s", Config.SEED_NODES, seed1, seed2);
        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(new Config(seedNodesOption));
        assertFalse(DUT.getSeedNodeAddresses().isEmpty());
        assertEquals(2, DUT.getSeedNodeAddresses().size());
        assertTrue(DUT.getSeedNodeAddresses().contains(new NodeAddress(seed1)));
        assertTrue(DUT.getSeedNodeAddresses().contains(new NodeAddress(seed2)));
    }

    @Test
    public void ignoreBannedSeedNodesWithWrongFormat() {
        String seed1 = "asdfbroken";
        String seed2 = "localhost:2002";
        String baseCurrencyNetwork = format("--%s=%s", Config.BASE_CURRENCY_NETWORK, "btc_regtest");
        String bannedSeedNodesOption = format("--%s=%s,%s", Config.BANNED_SEED_NODES, seed1, seed2);
        Config config = new Config(baseCurrencyNetwork, bannedSeedNodesOption);
        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(config);
        assertFalse(DUT.getSeedNodeAddresses().contains(new NodeAddress(seed2)));
    }

    @Test
    public void appliesDenyListSeedNodes() {
        String seed = "localhost:2002";
        String baseCurrencyNetwork = format("--%s=%s", Config.BASE_CURRENCY_NETWORK, "btc_regtest");
        Config config = new Config(baseCurrencyNetwork);
        Properties properties = new Properties();
        properties.setProperty("bannedSeedNodes", seed);
        DenyList denyList = DenyList.fromProperties(properties);

        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(config, denyList);

        assertFalse(DUT.getSeedNodeAddresses().contains(new NodeAddress(seed)));
    }

    @Test
    public void keepsLocalAndDenyListBansAndIgnoresFilterProvidedSeedNodesWhenConfigured() {
        String baseCurrencyNetwork = format("--%s=%s", Config.BASE_CURRENCY_NETWORK, "btc_regtest");
        Config config = new Config(baseCurrencyNetwork,
                "--ignoreNetworkFilter=true",
                "--bannedSeedNodes=localhost:2002",
                "--filterProvidedSeedNodes=localhost:4002");
        Properties properties = new Properties();
        properties.setProperty("bannedSeedNodes", "localhost:3002");
        DenyList denyList = DenyList.fromProperties(properties);

        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(config, denyList);

        // --ignoreNetworkFilter skips the filter-provided seed but does NOT skip deny-list bans.
        assertFalse(DUT.getSeedNodeAddresses().contains(new NodeAddress("localhost:2002")));
        assertFalse(DUT.getSeedNodeAddresses().contains(new NodeAddress("localhost:3002")));
        assertFalse(DUT.getSeedNodeAddresses().contains(new NodeAddress("localhost:4002")));
    }

    @AfterEach
    public void tearDown() {
        //restore default Config
        new Config();
    }
}
