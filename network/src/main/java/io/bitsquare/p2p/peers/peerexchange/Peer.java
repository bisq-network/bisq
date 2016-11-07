package io.bitsquare.p2p.peers.peerexchange;

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.p2p.NodeAddress;

import java.util.Date;

public final class Peer implements Payload, Persistable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final int MAX_FAILED_CONNECTION_ATTEMPTS = 5;

    public final NodeAddress nodeAddress;
    public final Date date;
    transient private int failedConnectionAttempts = 0;

    public Peer(NodeAddress nodeAddress) {
        this.nodeAddress = nodeAddress;
        this.date = new Date();
    }

    public void increaseFailedConnectionAttempts() {
        this.failedConnectionAttempts++;
    }

    public boolean tooManyFailedConnectionAttempts() {
        return failedConnectionAttempts >= MAX_FAILED_CONNECTION_ATTEMPTS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

        Peer that = (Peer) o;

        return !(nodeAddress != null ? !nodeAddress.equals(that.nodeAddress) : that.nodeAddress != null);

    }

    // We don't use the lastActivityDate for identity
    @Override
    public int hashCode() {
        return nodeAddress != null ? nodeAddress.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ReportedPeer{" +
                "address=" + nodeAddress +
                ", date=" + date +
                '}';
    }
}
