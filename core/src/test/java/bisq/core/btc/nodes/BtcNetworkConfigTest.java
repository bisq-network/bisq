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

import bisq.core.btc.setup.WalletConfig;

import bisq.network.Socks5MultiDiscovery;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class BtcNetworkConfigTest {
    private static final int MODE = 0;

    private WalletConfig walletConfig;

    @Before
    public void setUp() {
        walletConfig = mock(WalletConfig.class);
    }

    @Test
    public void testProposePeersWhenProxyPresentAndNoPeers() {
        BtcNetworkConfig.proposePeers(Collections.emptyList(), walletConfig, mock(Socks5Proxy.class), MODE, mock(NetworkParameters.class));

        verify(walletConfig, never()).setPeerNodes(any());
        verify(walletConfig).setDiscovery(any(Socks5MultiDiscovery.class));
    }

    @Test
    public void testProposePeersWhenProxyNotPresentAndNoPeers() {
        BtcNetworkConfig.proposePeers(Collections.emptyList(), walletConfig, null, MODE, mock(NetworkParameters.class));

        verify(walletConfig, never()).setDiscovery(any(Socks5MultiDiscovery.class));
        verify(walletConfig, never()).setPeerNodes(any());
    }

    @Test
    public void testProposePeersWhenPeersPresent() {
        BtcNetworkConfig.proposePeers(Collections.singletonList(mock(PeerAddress.class)), walletConfig, null, MODE, mock(NetworkParameters.class));

        verify(walletConfig, never()).setDiscovery(any(Socks5MultiDiscovery.class));
        verify(walletConfig).setPeerNodes(any());
    }
}
