package io.bitsquare.p2p.routing;

import io.bitsquare.p2p.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class Neighbor implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    public final Address address;
    private int pingNonce;

    public Neighbor(Address address) {
        this.address = address;
    }

    public void setPingNonce(int pingNonce) {
        this.pingNonce = pingNonce;
    }

    public int getPingNonce() {
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
