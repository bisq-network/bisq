package io.bitsquare.p2p.routing;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Random;

public class Neighbor implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    public final Connection connection;
    public final Address address;
    private long pingNonce;

    public Neighbor(Connection connection) {
        this.connection = connection;
        this.address = connection.getPeerAddress();
        pingNonce = new Random().nextLong();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            pingNonce = new Random().nextLong();
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
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
        if (!(o instanceof Neighbor)) return false;

        Neighbor neighbor = (Neighbor) o;

        return !(address != null ? !address.equals(neighbor.address) : neighbor.address != null);
    }

    @Override
    public String toString() {
        return "Neighbor{" +
                "address=" + address +
                ", pingNonce=" + pingNonce +
                '}';
    }
}
