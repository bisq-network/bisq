package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;

import javax.annotation.Nullable;

public final class DataRequest implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.getNetworkId();
    @Nullable
    public final NodeAddress senderNodeAddress;

    public DataRequest(@Nullable NodeAddress senderNodeAddress) {
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "GetDataRequest{" +
                "senderNodeAddress=" + senderNodeAddress +
                ", networkId=" + networkId +
                '}';
    }
}
