package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class Peer {
    private static final Logger log = LoggerFactory.getLogger(Peer.class);

    public final Connection connection;
    public final NodeAddress nodeAddress;
    public final long pingNonce;

    public Peer(Connection connection, NodeAddress nodeAddress) {
        this.connection = connection;
        this.nodeAddress = nodeAddress;

        pingNonce = new Random().nextLong();
    }

    @Override
    public int hashCode() {
        return nodeAddress != null ? nodeAddress.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;

        Peer peer = (Peer) o;

        return !(nodeAddress != null ? !nodeAddress.equals(peer.nodeAddress) : peer.nodeAddress != null);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "address=" + nodeAddress +
                ", pingNonce=" + pingNonce +
                ", connection=" + connection +
                '}';
    }
}
