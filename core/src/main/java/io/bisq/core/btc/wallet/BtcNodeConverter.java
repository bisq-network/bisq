package io.bisq.core.btc.wallet;

import io.bisq.core.btc.BitcoinNodes.BtcNode;
import org.bitcoinj.core.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

class BtcNodeConverter {
    private static final Logger log = LoggerFactory.getLogger(BtcNodeConverter.class);

    @Nullable
    PeerAddress convertClearNode(BtcNode node) {
        int port = node.getPort();

        PeerAddress result = create(node.getHostNameOrAddress(), port);
        if (result == null) {
            String address = node.getAddress();
            if (address != null) {
                result = create(address, port);
            } else {
                log.warn("Lookup failed, no ip address for node", node);
            }
        }
        return result;
    }

    @Nullable
    private static PeerAddress create(String hostName, int port) {
        try {
            InetSocketAddress address = new InetSocketAddress(hostName, port);
            return new PeerAddress(address.getAddress(), address.getPort());
        } catch (Throwable t) {
            log.error("Failed to create peer address", t);
            return null;
        }
    }
}

