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
import io.bisq.common.util.Utilities;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.MultiplexingDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.utils.DaemonThreadFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * <p>Supports peer discovery through DNS over Socks5 proxy with RESOLVE DNS extension.</p>
 * <p>
 * (As of this writing, only Tor is known to support the RESOLVE DNS extension.)
 * <p>
 * <p>Failure to resolve individual host names will not cause an Exception to be thrown.
 * However, if all hosts passed fail to resolve a PeerDiscoveryException will be thrown during getPeers().
 * </p>
 * <p>
 * <p>DNS seeds do not attempt to enumerate every peer on the network. {@link DnsDiscovery#getPeers(long, java.util.concurrent.TimeUnit)}
 * will return up to 30 random peers from the set of those returned within the timeout period. If you want more peers
 * to connect to, you need to discover them via other means (like addr broadcasts).</p>
 */
@Slf4j
public class Socks5DnsDiscovery extends MultiplexingDiscovery {

    /**
     * Supports finding peers through DNS A records. Community run DNS entry points will be used.
     *
     * @param netParams Network parameters to be used for port information.
     */
    public Socks5DnsDiscovery(Socks5Proxy proxy, NetworkParameters netParams) {
        this(proxy, netParams.getDnsSeeds(), netParams);
    }

    /**
     * Supports finding peers through DNS A records.
     *
     * @param dnsSeeds Host names to be examined for seed addresses.
     * @param params   Network parameters to be used for port information.
     */
    public Socks5DnsDiscovery(Socks5Proxy proxy, String[] dnsSeeds, NetworkParameters params) {
        super(params, buildDiscoveries(proxy, params, dnsSeeds));
    }

    private static List<PeerDiscovery> buildDiscoveries(Socks5Proxy proxy, NetworkParameters params, String[] seeds) {
        List<PeerDiscovery> discoveries = new ArrayList<>(seeds.length);
        for (String seed : seeds)
            discoveries.add(new Socks5DnsSeedDiscovery(proxy, params, seed));

        return discoveries;
    }

    @Override
    protected ExecutorService createExecutor() {
        // Attempted workaround for reported bugs on Linux in which gethostbyname does not appear to be properly
        // thread safe and can cause segfaults on some libc versions.
        if (Utilities.isLinux())
            return Executors.newSingleThreadExecutor(new ContextPropagatingThreadFactory("DNS seed lookups"));
        else
            return Executors.newFixedThreadPool(seeds.size(), new DaemonThreadFactory("DNS seed lookups"));
    }

    /**
     * Implements discovery from a single DNS host over Socks5 proxy with RESOLVE DNS extension.
     * With our DnsLookupTor (used to not leak at DNS lookup) version we only get one address instead a list of addresses like in DnsDiscovery.
     * We get repeated the call until we have received enough addresses.
     */
    public static class Socks5DnsSeedDiscovery implements PeerDiscovery {
        private final String hostname;
        private final NetworkParameters params;
        private final Socks5Proxy proxy;

        public Socks5DnsSeedDiscovery(Socks5Proxy proxy, NetworkParameters params, String hostname) {
            this.hostname = hostname;
            this.params = params;
            this.proxy = proxy;
        }

        /**
         * Returns peer addresses.  The actual DNS lookup is performed here.
         */
        @Override
        public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
            if (services != 0)
                throw new PeerDiscoveryException("DNS seeds cannot filter by services: " + services);
            try {
                InetSocketAddress addr = new InetSocketAddress(DnsLookupTor.lookup(proxy, hostname), params.getPort());
                return new InetSocketAddress[]{addr};
            } catch (Exception e) {
                throw new PeerDiscoveryException(e);
            }
        }

        @Override
        public void shutdown() {
        }

        @Override
        public String toString() {
            return hostname;
        }
    }
}
