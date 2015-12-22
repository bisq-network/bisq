package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class Peer {
    private static final Logger log = LoggerFactory.getLogger(Peer.class);

    public final Connection connection;
    public final Address address;
    private long pingNonce;

    public Peer(Connection connection) {
        this.connection = connection;
        this.address = connection.getPeerAddress();
        pingNonce = new Random().nextLong();
    }

    public long getPingNonce() {
        return pingNonce;
    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;

        Peer peer = (Peer) o;

        return !(address != null ? !address.equals(peer.address) : peer.address != null);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "address=" + address +
                ", pingNonce=" + pingNonce +
                ", connection=" + connection +
                '}';
    }
}
