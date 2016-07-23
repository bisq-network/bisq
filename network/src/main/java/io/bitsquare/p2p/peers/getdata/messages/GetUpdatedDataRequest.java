package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;

import javax.annotation.Nullable;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GetUpdatedDataRequest implements SendersNodeAddressMessage, GetDataRequest {
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

    private final NodeAddress senderNodeAddress;
    private final int nonce;

    public GetUpdatedDataRequest(NodeAddress senderNodeAddress, int nonce) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetUpdatedDataRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "GetUpdatedDataRequest{" +
                "messageVersion=" + messageVersion +
                ", senderNodeAddress=" + senderNodeAddress +
                ", nonce=" + nonce +
                '}';
    }
}
