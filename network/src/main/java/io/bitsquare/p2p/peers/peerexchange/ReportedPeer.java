package io.bitsquare.p2p.peers.peerexchange;

import io.bitsquare.app.Version;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.p2p.NodeAddress;

import java.util.Date;

public final class ReportedPeer implements Payload, Persistable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final NodeAddress nodeAddress;
    public final Date date;

    public ReportedPeer(NodeAddress nodeAddress) {
        this.nodeAddress = nodeAddress;
        this.date = new Date();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportedPeer)) return false;

        ReportedPeer that = (ReportedPeer) o;

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
