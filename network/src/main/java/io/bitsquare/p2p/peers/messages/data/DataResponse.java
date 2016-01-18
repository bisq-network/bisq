package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.storage.data.ProtectedData;

import java.util.HashSet;

public final class DataResponse implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;
    private final int networkId = Version.getNetworkId();
    
    public final HashSet<ProtectedData> set;

    public DataResponse(HashSet<ProtectedData> set) {
        this.set = set;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataResponse)) return false;

        DataResponse that = (DataResponse) o;

        return !(set != null ? !set.equals(that.set) : that.set != null);

    }

    @Override
    public int hashCode() {
        return set != null ? set.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GetDataResponse{" +
                "networkId=" + networkId +
                ", set=" + set +
                '}';
    }
}
