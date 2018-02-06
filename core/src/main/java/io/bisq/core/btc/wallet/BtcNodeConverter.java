package io.bisq.core.btc.wallet;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import io.bisq.network.DnsLookupTor;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.net.OnionCat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

class BtcNodeConverter {
    private static final Logger log = LoggerFactory.getLogger(BtcNodeConverter.class);

    @Nullable
    PeerAddress convertOnionHost(BtcNode node) {
        // no DNS lookup for onion addresses
        String onionAddress = Objects.requireNonNull(node.getOnionAddress());
        try {
            // OnionCat.onionHostToInetAddress converts onion to ipv6 representation
            // inetAddress is not used but required for wallet persistence. Throws nullPointer if not set.
            InetAddress inetAddress = OnionCat.onionHostToInetAddress(onionAddress);
            PeerAddress result = new PeerAddress(onionAddress, node.getPort());
            result.setAddr(inetAddress);
            return result;
        } catch (UnknownHostException e) {
            log.error("Failed to convert node", e);
            return null;
        }
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
                log.warn("Lookup failed, no address for node", node);
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
                log.warn("Lookup failed, no address for node", node);
            }
        }
        return result;
    }

    @Nullable
    private static PeerAddress create(Socks5Proxy proxy, String host, int port) {
        try {
            // We use DnsLookupTor to not leak with DNS lookup
            // Blocking call. takes about 600 ms ;-(
            InetAddress lookupAddress = DnsLookupTor.lookup(proxy, host);
            InetSocketAddress address = new InetSocketAddress(lookupAddress, port);
            return new PeerAddress(address.getAddress(), address.getPort());
        } catch (Exception e) {
            log.error("Failed to create peer address", e);
            return null;
        }
    }

    @Nullable
    private static PeerAddress create(String hostName, int port) {
        try {
            InetSocketAddress address = new InetSocketAddress(hostName, port);
            return new PeerAddress(address.getAddress(), address.getPort());
        } catch (Exception e) {
            log.error("Failed to create peer address", e);
            return null;
        }
    }
}

