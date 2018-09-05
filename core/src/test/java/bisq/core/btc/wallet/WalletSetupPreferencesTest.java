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

package bisq.core.btc.wallet;

import bisq.core.btc.BitcoinNodes;
import bisq.core.btc.BitcoinNodes.BtcNode;
import bisq.core.user.Preferences;

import java.util.List;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static bisq.core.btc.BitcoinNodes.BitcoinNodesOption.CUSTOM;
import static bisq.core.btc.BitcoinNodes.BitcoinNodesOption.PUBLIC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Preferences.class)
public class WalletSetupPreferencesTest {
    @Test
    public void testSelectPreferredNodesWhenPublicOption() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(PUBLIC.ordinal());

        WalletSetupPreferences preferences = new WalletSetupPreferences(delegate);
        List<BtcNode> nodes = preferences.selectPreferredNodes(mock(BitcoinNodes.class));

        assertTrue(nodes.isEmpty());
    }

    @Test
    public void testSelectPreferredNodesWhenCustomOption() {
        Preferences delegate = mock(Preferences.class);
        when(delegate.getBitcoinNodesOptionOrdinal()).thenReturn(CUSTOM.ordinal());
        when(delegate.getBitcoinNodes()).thenReturn("aaa.onion,bbb.onion");

        WalletSetupPreferences preferences = new WalletSetupPreferences(delegate);
        List<BtcNode> nodes = preferences.selectPreferredNodes(mock(BitcoinNodes.class));

        assertEquals(2, nodes.size());
    }
}
