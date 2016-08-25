package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.app.Capabilities;
import io.bitsquare.app.Version;
import io.bitsquare.p2p.messaging.SupportedCapabilitiesMessage;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;

public final class GetDataResponse implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private final int messageVersion = Version.getP2PMessageVersion();

    public final HashSet<ProtectedStorageEntry> dataSet;
    public final int requestNonce;
    public final boolean isGetUpdatedDataResponse;
    
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetDataResponse(HashSet<ProtectedStorageEntry> dataSet, int requestNonce, boolean isGetUpdatedDataResponse) {
        this.dataSet = dataSet;
        this.requestNonce = requestNonce;
        this.isGetUpdatedDataResponse = isGetUpdatedDataResponse;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
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
                ", supportedCapabilities=" + supportedCapabilities +
                ", isGetUpdatedDataResponse=" + isGetUpdatedDataResponse +
                '}';
    }
}
