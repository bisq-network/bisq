package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.storage.data.ProtectedData;

import java.util.HashSet;

public final class GetDataResponse implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;
    private final int networkId = Version.getNetworkId();

    public final HashSet<ProtectedData> dataSet;
    public final long requestNonce;

    public GetDataResponse(HashSet<ProtectedData> dataSet, long requestNonce) {
        this.dataSet = dataSet;
        this.requestNonce = requestNonce;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GetDataResponse)) return false;

        GetDataResponse that = (GetDataResponse) o;

        return !(dataSet != null ? !dataSet.equals(that.dataSet) : that.dataSet != null);

    }

    @Override
    public int hashCode() {
        return dataSet != null ? dataSet.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GetDataResponse{" +
                "networkId=" + networkId +
                ", dataSet=" + dataSet +
                ", requestNonce=" + requestNonce +
                '}';
    }
}
