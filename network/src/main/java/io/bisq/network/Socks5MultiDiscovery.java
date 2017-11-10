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

package io.bisq.network;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.net.discovery.SeedPeers;
import org.bitcoinj.params.MainNetParams;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


/**
 * This class implements various types of discovery over Socks5,
 * which can be enabled/disabled via constructor flag.
 */
@Slf4j
public class Socks5MultiDiscovery implements PeerDiscovery {

    public static final int SOCKS5_DISCOVER_ADDR = 0x0001;
    public static final int SOCKS5_DISCOVER_DNS = 0x0010;
    public static final int SOCKS5_DISCOVER_ONION = 0x0100;
    public static final int SOCKS5_DISCOVER_ALL = 0x1111;

    private final ArrayList<PeerDiscovery> discoveryList = new ArrayList<>();

    /**
     * Supports finding peers by hostname over a socks5 proxy.
     *
     * @param proxy  proxy the socks5 proxy to connect over.
     * @param params param to be used for seed and port information.
     * @param mode   specify discovery mode, OR'd together. one or more of:
     *               SOCKS5_DISCOVER_ADDR
     *               SOCKS5_DISCOVER_DNS
     *               SOCKS5_DISCOVER_ONION
     *               SOCKS5_DISCOVER_ALL
     */
    public Socks5MultiDiscovery(Socks5Proxy proxy, NetworkParameters params, int mode) {
        if ((mode & SOCKS5_DISCOVER_ONION) != 0)
            discoveryList.add(new Socks5SeedOnionDiscovery(proxy, params));

        // Testnet has no addrSeeds so SeedPeers is not supported (would throw a nullPointer)
        if ((mode & SOCKS5_DISCOVER_ADDR) != 0 && params == MainNetParams.get())
            // note:  SeedPeers does not perform any network operations, so does not use proxy.
            discoveryList.add(new SeedPeers(params));

        if ((mode & SOCKS5_DISCOVER_DNS) != 0)
            discoveryList.add(new Socks5DnsDiscovery(proxy, params));
    }

    /**
     * Returns an array containing all the Bitcoin nodes that have been discovered.
     */
    @Override
    public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        ArrayList<InetSocketAddress> list = new ArrayList<>();
        for (PeerDiscovery discovery : discoveryList) {
            list.addAll(Arrays.asList(discovery.getPeers(services, timeoutValue, timeoutUnit)));
        }

        return list.toArray(new InetSocketAddress[list.size()]);
    }

    @Override
    public void shutdown() {
        //TODO should we add a DnsLookupTor.shutdown() ?
    }
}
