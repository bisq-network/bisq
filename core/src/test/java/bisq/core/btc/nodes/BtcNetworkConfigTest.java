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

    private WalletConfig delegate;

    @Before
    public void setUp() {
        delegate = mock(WalletConfig.class);
    }

    @Test
    public void testProposePeersWhenProxyPresentAndNoPeers() {
        BtcNetworkConfig config = new BtcNetworkConfig(delegate, mock(NetworkParameters.class), MODE,
                mock(Socks5Proxy.class));
        config.proposePeers(Collections.emptyList());

        verify(delegate, never()).setPeerNodes(any());
        verify(delegate).setDiscovery(any(Socks5MultiDiscovery.class));
    }

    @Test
    public void testProposePeersWhenProxyNotPresentAndNoPeers() {
        BtcNetworkConfig config = new BtcNetworkConfig(delegate, mock(NetworkParameters.class), MODE,
                null);
        config.proposePeers(Collections.emptyList());

        verify(delegate, never()).setDiscovery(any(Socks5MultiDiscovery.class));
        verify(delegate, never()).setPeerNodes(any());
    }

    @Test
    public void testProposePeersWhenPeersPresent() {
        BtcNetworkConfig config = new BtcNetworkConfig(delegate, mock(NetworkParameters.class), MODE,
                null);
        config.proposePeers(Collections.singletonList(mock(PeerAddress.class)));

        verify(delegate, never()).setDiscovery(any(Socks5MultiDiscovery.class));
        verify(delegate).setPeerNodes(any());
    }
}
