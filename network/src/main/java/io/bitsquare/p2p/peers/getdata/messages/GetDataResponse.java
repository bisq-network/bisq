package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.storage.ProtectedData;

import java.util.HashSet;

public final class GetDataResponse implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private final int messageVersion = Version.getP2PMessageVersion();

    public final HashSet<ProtectedData> dataSet;
    public final long requestNonce;

    public GetDataResponse(HashSet<ProtectedData> dataSet, long requestNonce) {
        this.dataSet = dataSet;
        this.requestNonce = requestNonce;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "GetDataResponse{" +
                "messageVersion=" + messageVersion +
                ", dataSet.size()=" + dataSet.size() +
                ", requestNonce=" + requestNonce +
                '}';
    }
}
