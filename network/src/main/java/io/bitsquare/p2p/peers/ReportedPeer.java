package io.bitsquare.p2p.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;

import java.io.Serializable;
import java.util.Date;

public class ReportedPeer implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final NodeAddress nodeAddress;
    public final Date lastActivityDate;

    public ReportedPeer(NodeAddress nodeAddress, Date lastActivityDate) {
        this.nodeAddress = nodeAddress;
        this.lastActivityDate = lastActivityDate;
    }

    public ReportedPeer(NodeAddress nodeAddress) {
        this(nodeAddress, null);
    }

    // We don't use the lastActivityDate for identity
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
                ", lastActivityDate=" + lastActivityDate +
                '}';
    }
}
