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

/**
 * Copyright 2011 Micheal Swiggs
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bisq.core.btc.nodes;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

// TODO not used anymore. Not sure if it was replaced by something else or removed by accident.
/**
 * SeedPeersSocks5Dns resolves peers via Proxy (Socks5) remote DNS.
 */
public class SeedPeersSocks5Dns implements PeerDiscovery {
    private final Socks5Proxy proxy;
    private final NetworkParameters params;
    private final InetSocketAddress[] seedAddrs;
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private InetSocketAddress[] seedAddrsIP;
    private int pnseedIndex;

    private final InetSocketAddress[] seedAddrsResolved;

    private static final Logger log = LoggerFactory.getLogger(SeedPeersSocks5Dns.class);

    /**
     * Supports finding peers by hostname over a socks5 proxy.
     */
    public SeedPeersSocks5Dns(Socks5Proxy proxy, NetworkParameters params) {

        this.proxy = proxy;
        this.params = params;
        this.seedAddrs = convertAddrsString(params.getDnsSeeds(), params.getPort());

        /*
        // This is an example of how .onion servers could be used.  Unfortunately there is presently no way
        // to hand the onion address (or a connected socket) back to bitcoinj without it crashing in PeerAddress.
        // note:  the onion addresses should be added into bitcoinj NetworkParameters classes, eg for mainnet, testnet
        //        not here!
        this.seedAddrs = new InetSocketAddress[]{InetSocketAddress.createUnresolved("cajrifqkvalh2ooa.onion", 8333),
                InetSocketAddress.createUnresolved("bk7yp6epnmcllq72.onion", 8333)
        };
        */

        //TODO seedAddrsIP is never written; not used method...
        seedAddrsResolved = new InetSocketAddress[seedAddrs.length];
        System.arraycopy(seedAddrsIP, seedAddrs.length, seedAddrsResolved,
                seedAddrs.length, seedAddrsResolved.length - seedAddrs.length);
    }

    /**
     * Acts as an iterator, returning the address of each node in the list sequentially.
     * Once all the list has been iterated, null will be returned for each subsequent query.
     *
     * @return InetSocketAddress - The address/port of the next node.
     * @throws PeerDiscoveryException
     */
    @Nullable
    public InetSocketAddress getPeer() throws PeerDiscoveryException {
        try {
            return nextPeer();
        } catch (PeerDiscoveryException e) {
            throw new PeerDiscoveryException(e);
        }
    }

    /**
     * worker for getPeer()
     */
    @Nullable
    private InetSocketAddress nextPeer() throws PeerDiscoveryException {
        if (seedAddrs == null || seedAddrs.length == 0) {
            throw new PeerDiscoveryException("No IP address seeds configured; unable to find any peers");
        }

        if (pnseedIndex >= seedAddrsResolved.length) {
            return null;
        }
        if (seedAddrsResolved[pnseedIndex] == null) {
            seedAddrsResolved[pnseedIndex] = lookup(proxy, seedAddrs[pnseedIndex]);
        }
        log.error("SeedPeersSocks5Dns::nextPeer: " + seedAddrsResolved[pnseedIndex]);

        return seedAddrsResolved[pnseedIndex++];
    }

    /**
     * Returns an array containing all the Bitcoin nodes within the list.
     */
    @Override
    public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        if (services != 0)
            throw new PeerDiscoveryException("DNS seeds cannot filter by services: " + services);
        return allPeers();
    }

    /**
     * returns all seed peers, performs hostname lookups if necessary.
     */
    private InetSocketAddress[] allPeers() {
        for (int i = 0; i < seedAddrsResolved.length; ++i) {
            if (seedAddrsResolved[i] == null) {
                seedAddrsResolved[i] = lookup(proxy, seedAddrs[i]);
            }
        }
        return seedAddrsResolved;
    }

    /**
     * Resolves a hostname via remote DNS over socks5 proxy.
     */
    @Nullable
    public static InetSocketAddress lookup(Socks5Proxy proxy, InetSocketAddress addr) {
        if (!addr.isUnresolved()) {
            return addr;
        }
        try {
            SocksSocket proxySocket = new SocksSocket(proxy, addr.getHostString(), addr.getPort());
            InetAddress addrResolved = proxySocket.getInetAddress();
            proxySocket.close();
            if (addrResolved != null) {
                //log.debug("Resolved " + addr.getHostString() + " to " + addrResolved.getHostAddress());
                return new InetSocketAddress(addrResolved, addr.getPort());
            } else {
                // note: .onion nodes fall in here when proxy is Tor. But they have no IP address.
                // Unfortunately bitcoinj crashes in PeerAddress if it finds an unresolved address.
                log.error("Connected to " + addr.getHostString() + ".  But did not resolve to address.");
            }
        } catch (Exception e) {
            log.warn("Error resolving " + addr.getHostString() + ". Exception:\n" + e.toString());
        }
        return null;
    }

    /**
     * Converts an array of hostnames to array of unresolved InetSocketAddress
     */
    private InetSocketAddress[] convertAddrsString(String[] addrs, int port) {
        InetSocketAddress[] list = new InetSocketAddress[addrs.length];
        for (int i = 0; i < addrs.length; i++) {
            list[i] = InetSocketAddress.createUnresolved(addrs[i], port);
        }
        return list;
    }

    @Override
    public void shutdown() {
    }
}
