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

import bisq.network.DnsLookupException;
import bisq.network.DnsLookupTor;

import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.net.OnionCatConverter;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

class BtcNodeConverter {
    private static final Logger log = LoggerFactory.getLogger(BtcNodeConverter.class);

    private final Facade facade;

    BtcNodeConverter() {
        this.facade = new Facade();
    }

    BtcNodeConverter(Facade facade) {
        this.facade = facade;
    }

    @Nullable
    PeerAddress convertOnionHost(BtcNode node) {
        // no DNS lookup for onion addresses
        String onionAddress = Objects.requireNonNull(node.getOnionAddress());
        return new PeerAddress(onionAddress, node.getPort());
    }

    @Nullable
    PeerAddress convertClearNode(BtcNode node) {
        int port = node.getPort();

        PeerAddress result = create(node.getHostNameOrAddress(), port);
        if (result == null) {
            String address = node.getAddress();
            if (address != null) {
                result = create(address, port);
            } else {
                log.warn("Lookup failed, no address for node {}", node);
            }
        }
        return result;
    }

    @Nullable
    PeerAddress convertWithTor(BtcNode node, Socks5Proxy proxy) {
        int port = node.getPort();

        PeerAddress result = create(proxy, node.getHostNameOrAddress(), port);
        if (result == null) {
            String address = node.getAddress();
            if (address != null) {
                result = create(proxy, address, port);
            } else {
                log.warn("Lookup failed, no address for node {}", node);
            }
        }
        return result;
    }

    @Nullable
    private PeerAddress create(Socks5Proxy proxy, String host, int port) {
        try {
            // We use DnsLookupTor to not leak with DNS lookup
            // Blocking call. takes about 600 ms ;-(
            InetAddress lookupAddress = facade.torLookup(proxy, host);
            InetSocketAddress address = new InetSocketAddress(lookupAddress, port);
            return new PeerAddress(address);
        } catch (Exception e) {
            log.error("Failed to create peer address", e);
            return null;
        }
    }

    @Nullable
    private static PeerAddress create(String hostName, int port) {
        try {
            InetSocketAddress address = new InetSocketAddress(hostName, port);
            return new PeerAddress(address);
        } catch (Exception e) {
            log.error("Failed to create peer address", e);
            return null;
        }
    }

    static class Facade {
        InetAddress onionHostToInetAddress(String onionAddress) throws UnknownHostException {
            return OnionCatConverter.onionHostToInetAddress(onionAddress);
        }

        InetAddress torLookup(Socks5Proxy proxy, String host) throws DnsLookupException {
            return DnsLookupTor.lookup(proxy, host);
        }
    }
}

