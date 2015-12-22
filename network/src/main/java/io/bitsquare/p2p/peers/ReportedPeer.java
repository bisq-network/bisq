package io.bitsquare.p2p.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

import java.io.Serializable;
import java.util.Date;

public class ReportedPeer implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final Date lastActivityDate;

    public ReportedPeer(Address address, Date lastActivityDate) {
        this.address = address;
        this.lastActivityDate = lastActivityDate;
    }

    public ReportedPeer(Address address) {
        this(address, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportedPeer)) return false;

        ReportedPeer that = (ReportedPeer) o;

        return !(address != null ? !address.equals(that.address) : that.address != null);

    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ReportedPeer{" +
                "address=" + address +
                ", lastActivityDate=" + lastActivityDate +
                '}';
    }
}
