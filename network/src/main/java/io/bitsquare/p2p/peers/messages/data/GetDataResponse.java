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
    public String toString() {
        return "GetDataResponse{" +
                "networkId=" + networkId +
                ", dataSet.size()=" + dataSet.size() +
                ", requestNonce=" + requestNonce +
                '}';
    }
}
