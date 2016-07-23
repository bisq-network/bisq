package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;

public final class GetDataResponse implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private final int messageVersion = Version.getP2PMessageVersion();
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    public final HashSet<ProtectedStorageEntry> dataSet;
    public final int requestNonce;

    public GetDataResponse(HashSet<ProtectedStorageEntry> dataSet, int requestNonce) {
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
