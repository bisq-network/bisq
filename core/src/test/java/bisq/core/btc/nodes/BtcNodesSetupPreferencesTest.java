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

package bisq.core.btc.nodes;

import bisq.core.btc.nodes.BtcNodes.BtcNode;
import bisq.core.filter.DenyList;
import bisq.core.user.Preferences;

import bisq.common.config.Config;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static bisq.core.btc.nodes.BtcNodes.BitcoinNodesOption.CUSTOM;
import static bisq.core.btc.nodes.BtcNodes.BitcoinNodesOption.PROVIDED;
import static bisq.core.btc.nodes.BtcNodes.BitcoinNodesOption.PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BtcNodesSetupPreferencesTest {
    @Test
    public void testSelectPreferredNodesWhenPublicOption() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(PUBLIC.ordinal());

        BtcNodesSetupPreferences preferences = new BtcNodesSetupPreferences(delegate, Config.DEFAULT_NUM_CONNECTIONS_FOR_BTC_PUBLIC, null);
        List<BtcNode> nodes = preferences.selectPreferredNodes(mock(BtcNodes.class));

        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testSelectPreferredNodesWhenCustomOption() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(CUSTOM.ordinal());
        when(delegate.getBitcoinNodes()).thenReturn("aaa.onion,bbb.onion");

        BtcNodesSetupPreferences preferences = new BtcNodesSetupPreferences(delegate, Config.DEFAULT_NUM_CONNECTIONS_FOR_BTC_PUBLIC, null);
        List<BtcNode> nodes = preferences.selectPreferredNodes(mock(BtcNodes.class));

        assertEquals(2, nodes.size());
    }

    @Test
    public void testSelectPreferredNodesAppliesDenyList() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(PROVIDED.ordinal());

        BtcNode alice = new BtcNode(null, "alice.onion", null, BtcNode.DEFAULT_PORT, "@alice");
        BtcNode bob = new BtcNode(null, "bob.onion", null, BtcNode.DEFAULT_PORT, "@bob");
        BtcNodes btcNodes = mock(BtcNodes.class);
        when(btcNodes.getProvidedBtcNodes()).thenReturn(List.of(alice, bob));

        Properties properties = new Properties();
        properties.setProperty("bannedBtcNodes", "bob.onion:" + BtcNode.DEFAULT_PORT);
        DenyList denyList = DenyList.fromProperties(properties);

        BtcNodesSetupPreferences preferences = new BtcNodesSetupPreferences(delegate,
                Config.DEFAULT_NUM_CONNECTIONS_FOR_BTC_PUBLIC,
                new Config(),
                denyList);
        List<BtcNode> nodes = preferences.selectPreferredNodes(btcNodes);

        assertEquals(List.of(alice), nodes);
    }

    @Test
    public void testSelectPreferredNodesIgnoresPersistedNetworkFilterNodesWhenConfigured() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(PROVIDED.ordinal());

        BtcNode alice = new BtcNode(null, "alice.onion", null, BtcNode.DEFAULT_PORT, "@alice");
        BtcNode bob = new BtcNode(null, "bob.onion", null, BtcNode.DEFAULT_PORT, "@bob");
        BtcNodes btcNodes = mock(BtcNodes.class);
        when(btcNodes.getProvidedBtcNodes()).thenReturn(List.of(alice, bob));

        Config config = new Config("--ignoreNetworkFilter=true",
                "--bannedBtcNodes=bob.onion:" + BtcNode.DEFAULT_PORT,
                "--filterProvidedBtcNodes=carol.onion:" + BtcNode.DEFAULT_PORT);
        Properties properties = new Properties();
        properties.setProperty("bannedBtcNodes", "alice.onion:" + BtcNode.DEFAULT_PORT);
        DenyList denyList = DenyList.fromProperties(properties);

        BtcNodesSetupPreferences preferences = new BtcNodesSetupPreferences(delegate,
                Config.DEFAULT_NUM_CONNECTIONS_FOR_BTC_PUBLIC,
                config,
                denyList);
        List<BtcNode> nodes = preferences.selectPreferredNodes(btcNodes);

        assertEquals(List.of(bob), nodes);
    }
}
